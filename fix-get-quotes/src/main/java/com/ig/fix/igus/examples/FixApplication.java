package com.ig.fix.igus.examples;

import lombok.extern.slf4j.Slf4j;
import quickfix.ApplicationAdapter;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.UnsupportedMessageType;
import quickfix.field.SecurityListRequestType;
import quickfix.field.SecurityReqID;
import quickfix.field.SubscriptionRequestType;
import quickfix.fix50sp2.SecurityListRequest;
import quickfix.fixt11.Reject;

@Slf4j
public class FixApplication extends ApplicationAdapter {

	private MessageCracker messageCracker;

	public FixApplication(MessageCracker messageCracker) {
		this.messageCracker = messageCracker;
	}
	
	@Override
	public void onLogon(SessionID sessionId) {
		requestSecurityList(sessionId);
	}

	private void requestSecurityList(SessionID sessionId) {
		SecurityListRequest request = new SecurityListRequest(new SecurityReqID("secList-req-1"),
				new SecurityListRequestType(SecurityListRequestType.ALL_SECURITIES));
		request.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT));
		try {
			Session.sendToTarget(request, sessionId);
		} catch (SessionNotFound e) {
			log.warn("this should not happen for sessionId={} not sending message",sessionId, e);
		}
	}
	
	@Override
	public void fromAdmin(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
		if(message instanceof Reject) {
			log.warn("server is rejecting our message, is it poppulated correctly? {}",message);
		}
	}
	
	@Override
	public void fromApp(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		//handle message if no handler method then reject
		messageCracker.crack(message, sessionId);
	}
	
}
