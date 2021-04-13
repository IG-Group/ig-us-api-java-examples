package com.ig.fix.igus.examples;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;
import quickfix.FieldNotFound;
import quickfix.MessageCracker;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.field.QuoteReqID;
import quickfix.field.SecurityID;
import quickfix.field.SubscriptionRequestType;
import quickfix.field.Symbol;
import quickfix.fix50sp2.BusinessMessageReject;
import quickfix.fix50sp2.Quote;
import quickfix.fix50sp2.QuoteRequest;
import quickfix.fix50sp2.QuoteRequestReject;
import quickfix.fix50sp2.SecurityList;
import quickfix.fix50sp2.SecurityList.NoRelatedSym;
import quickfix.fix50sp2.component.QuotReqGrp;

@Slf4j
public class FixMessageCracker extends MessageCracker {
	
	public void onMessage(BusinessMessageReject message, SessionID sessionID) throws FieldNotFound {
		//if this is not implemented then we will reject the rejection
		log.warn("message rejected please investigate {}",message);
	}
	
	public void onMessage(SecurityList securityList, SessionID sessionID) throws FieldNotFound, SessionNotFound {
		//subscribe to quotes for all instruments
		// see https://www.quickfixj.org/usermanual/2.1.0/usage/repeating_groups.html
		int instrumentCount = securityList.getNoRelatedSym().getValue();
		NoRelatedSym group = new SecurityList.NoRelatedSym();
		for(int i=1; i<= instrumentCount;++i) {
			//1-indexed
			securityList.getGroup(i, group);
			
			String secId = group.get(new SecurityID()).getValue();
			//TODO: filter here for instruments you care about
			
			log.info("subscribing to {}", secId );
			QuoteRequest request = new QuoteRequest(new QuoteReqID(secId));
            QuotReqGrp.NoRelatedSym symbol = new QuotReqGrp.NoRelatedSym();
            symbol.set(new Symbol(group.getSecurityID().getValue()));
            symbol.set(group.getSecurityID());
            symbol.set(group.getSecurityIDSource());
            request.addGroup(symbol);
            request.setField(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_AND_UPDATES));
            Session.sendToTarget(request,sessionID);			
		}
	}
	
	public void onMessage(QuoteRequestReject rejection, SessionID sessionID) {
		log.warn("quote request was rejected {}", rejection);
	}
	
	public void onMessage(Quote quote, SessionID sessionID) throws FieldNotFound, SessionNotFound {
		String secId = quote.getQuoteReqID().getValue();//cheap trick: use the quoteReqId to look the symbol (or in this case contain the symbol)
		BigDecimal bid = quote.getBidPx().getValue();
		BigDecimal offer = quote.getOfferPx().getValue();
		log.info("secId={} bid={}, offer={}",secId, bid,offer);
	}
}
