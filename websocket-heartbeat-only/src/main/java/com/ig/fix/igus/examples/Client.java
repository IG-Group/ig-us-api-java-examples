package com.ig.fix.igus.examples;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ig.orchestrations.fixp.Establish;
import com.ig.orchestrations.fixp.FlowType;
import com.ig.orchestrations.fixp.IgExtensionCredentials;
import com.ig.orchestrations.fixp.Negotiate;
import com.ig.orchestrations.fixp.NegotiationResponse;
import com.ig.orchestrations.fixp.UnsequencedHeartbeat;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@ConfigurationProperties(prefix = "client")
@Slf4j
public class Client {
	/**
	 * the url to the websocket begins with wss:// or ws://
	 */
	@Setter
	private URI url;
	@Setter
	private String username;
	@Setter
	private String password;
	@Setter
	private Duration reconnectInterval;
	@Setter
	private Duration heartbeatInterval;

	@Autowired
	private ObjectMapper objectMapper;

	private Disposable connectionDisposable;

	@PostConstruct
	public void startConnection() throws MalformedURLException, URISyntaxException {
		connectionDisposable = reconnectOnError(makeWebsocketHandler());
	}

	@PreDestroy
	public void stopConnection() throws MalformedURLException, URISyntaxException {
		connectionDisposable.dispose();
	}

	private Disposable reconnectOnError(WebSocketHandler websocketHandler)
			throws MalformedURLException, URISyntaxException {
		return Flux.interval(reconnectInterval).startWith(-1L).onBackpressureLatest()//
				.log("outer-loop")//
				.<Void>flatMap(ignoredValue -> new ReactorNettyWebSocketClient()//
						.execute(url, websocketHandler)//
						.onErrorResume(t -> {
							log.warn("ignoring (will reconnect)", t);
							return Mono.empty();
						}), 1)//
				.subscribe();
	}

	private WebSocketHandler makeWebsocketHandler() {
		return session -> session.send(session.receive()//
				.map(msg -> msg.getPayloadAsText()).log("ws-in")// TODO: this will not close a connection if the server
																// stops heartbeating
				.map(msg -> toJsonNode(msg)).flatMap(jsonNode -> handleMessage(jsonNode))
				.switchMap(str -> followWithHeartbeat(str))//
				.startWith(initiateLogin()).log("ws-out")//
				.map(str -> session.textMessage(str)));
	}

	private Flux<String> followWithHeartbeat(String msg) {
		return Flux.interval(heartbeatInterval)//
				.map(ignore -> new UnsequencedHeartbeat("UnsequencedHeartbeat"))//
				.map(o -> objectToJson(o))//
				.startWith(msg);
	}

	private String initiateLogin() {
		Negotiate msg = new Negotiate(UUID.randomUUID(), //
				System.currentTimeMillis() * 1_000_000, FlowType.UNSEQUENCED, //
				new IgExtensionCredentials("login", username + ":" + password), //
				"Negotiate");// the binding requires the message type here
		return objectToJson(msg);
	}

	private Flux<String> handleMessage(JsonNode jsonNode) {
		try {
			String adminMsgType = Optional.ofNullable(jsonNode.get("MessageType")).map(i -> i.asText()).orElse("");
	
			switch (adminMsgType) {
			case "NegotiationReject":
				// nop as the other side will close
				return Flux.empty();
			case "NegotiationResponse":
				log.info("NegotiationResponse {}", jsonNode);
				NegotiationResponse negotiationResponse = jsonNodeTo(jsonNode, NegotiationResponse.class);
				log.info("NegotiationResponse {}", negotiationResponse);
				Establish establish = new Establish(negotiationResponse.getSessionId(), //
						System.currentTimeMillis() * 1_000_000, //milli to nano
						heartbeatInterval.toMillis() * 2, // advertise twice the heartbeat frequency to allow time for the
															// heartbeats to reach the server
						"Establish");
				return Flux.just(objectToJson(establish));
			case "EstablishmentAck":
				// logged in
				return Flux.empty();
			case "":
				// it is probably an application message //application message logic here
				return Flux.empty();
			default:
				return Flux.empty();
			}
		} catch (RuntimeException e) {
			log.error("while handling {}", jsonNode, e);
			throw e; //when this is thrown the connection will close
		}
	}

	private String objectToJson(Object msg) {
		try {
			return objectMapper.writeValueAsString(msg);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("this should not happen", e);
		}
	}

	private <T> T jsonNodeTo(JsonNode node, Class<T> clazz) {
		try {
			return objectMapper.treeToValue(node, clazz);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("this should not happen", e);
		}
	}

	private JsonNode toJsonNode(String msg) {
		try {
			return objectMapper.readTree(msg);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
