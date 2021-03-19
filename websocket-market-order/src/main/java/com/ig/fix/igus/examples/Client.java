package com.ig.fix.igus.examples;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Date;
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
import com.ig.orchestrations.us.rfed.fields.ApplVerID;
import com.ig.orchestrations.us.rfed.fields.ExecType;
import com.ig.orchestrations.us.rfed.fields.MsgType;
import com.ig.orchestrations.us.rfed.fields.OrdType;
import com.ig.orchestrations.us.rfed.fields.SecurityIDSource;
import com.ig.orchestrations.us.rfed.fields.Side;
import com.ig.orchestrations.us.rfed.fields.TimeInForce;
import com.ig.orchestrations.us.rfed.messages.ExecutionReport;
import com.ig.orchestrations.us.rfed.messages.NewOrderSingle;

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
	private String account;
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
				log.debug("logged in will trade");
				return Flux.just(objectToJson(newOrderSingle()));
			case "":
				log.debug("probably AppMessge");
				String applicationMessageType = Optional.ofNullable(jsonNode.get("MsgType")).map(i -> i.asText()).orElse("");
				if (!applicationMessageType.isBlank()) {
					return handleApplicationMessage(applicationMessageType, jsonNode)//
							.map(o->objectToJson(o));
				}
				return Flux.empty();
			default:
				return Flux.empty();
			}
		} catch (RuntimeException e) {
			log.error("while handling {}", jsonNode, e);
			throw e; //when this is thrown the connection will close
		}
	}

	private Flux<Object> handleApplicationMessage(String applicationMessageType, JsonNode jsonNode) {
		log.debug("handleApplicationMessage {}", applicationMessageType);
		switch (applicationMessageType) {
		case "ExecutionReport":
			ExecutionReport executionReport = jsonNodeTo(jsonNode,ExecutionReport.class);
			log.info("got execReport status={}",executionReport.getExecType());
			return Flux.empty();
		default:
			log.warn("received unsupported msgType={}",applicationMessageType);
			return Flux.empty();
		}
	}

	/**
	 * New order single for a specific hardcoded instrument. USe the SecurityListRequest to get market data for available instrument
	 * <pre>
	 test instrument
    {
        "Symbol":"GBP/USD",
        "SecurityID":"CS.D.GBPUSD.CZD.IP",
        "SecurityIDSource":"MarketplaceAssignedIdentifier",
        "SecAltIDGrp":[],
        "SecurityGroup":"CURRENCIES",
        "ContractMultiplier":1E+5,
        "SecurityDesc":"GBP100,000 Contract",
        "ShortSaleRestriction":"NoRestrictions",
        "AttrbGrp":[{"InstrAttribType":"DealableCurrencies","InstrAttribValue":"USD"}],
        "UndInstrmtGrp":[],"Currency":"USD"
    }
</pre>
	 * 
	 */
	private NewOrderSingle newOrderSingle() {
		NewOrderSingle req = new NewOrderSingle();
		req.setApplVerID(ApplVerID.FIX_50_SP_2);
		req.setMsgType(MsgType.NEW_ORDER_SINGLE);
		req.setSendingTime(new Date());
		req.setClOrdID("buy-1#"+System.currentTimeMillis());
		req.setSide(Side.BUY);
		req.setSecurityID("CS.D.GBPUSD.CZD.IP");
		req.setSecurityIDSource(SecurityIDSource.MARKETPLACE_ASSIGNED_IDENTIFIER);
		req.setCurrency("USD");//comes from DealableCurrencies
		req.setOrderQty(BigDecimal.ONE);
		req.setOrdType(OrdType.MARKET);
		req.setTimeInForce(TimeInForce.FILL_OR_KILL);
		req.setTransactTime(new Date());
		req.setAccount(account);
		return req;
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
