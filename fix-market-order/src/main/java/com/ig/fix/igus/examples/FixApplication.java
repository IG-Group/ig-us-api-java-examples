package com.ig.fix.igus.examples;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import quickfix.ApplicationAdapter;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.UnsupportedMessageType;
import quickfix.field.Account;
import quickfix.field.ClOrdID;
import quickfix.field.Currency;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.SecurityID;
import quickfix.field.SecurityIDSource;
import quickfix.field.Side;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import quickfix.fix50sp2.NewOrderSingle;

@Slf4j
public class FixApplication extends ApplicationAdapter {

	private MessageCracker messageCracker;
    private String account;

	public FixApplication(MessageCracker messageCracker, String account) {
		this.messageCracker = messageCracker;
        this.account = account;
	}
	
	@Override
	public void onLogon(SessionID sessionId) {
	    log.info("logged on will send trade");
	    try {
            Session.sendToTarget(makeNewOrderSingle(), sessionId);
        } catch (SessionNotFound e) {
            log.error("this should never happen. (race condition?)",e);
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
    private NewOrderSingle makeNewOrderSingle() {
        NewOrderSingle req = new NewOrderSingle();
        req.set(new ClOrdID("buy-1#"+System.currentTimeMillis()));
        req.set(new Side(Side.BUY));
        req.set(new SecurityID("CS.D.GBPUSD.CZD.IP"));
        req.set(new SecurityIDSource(SecurityIDSource.MARKETPLACE_ASSIGNED_IDENTIFIER));
        req.set(new Currency("USD"));//comes from DealableCurrencies
        req.set(new OrderQty(BigDecimal.ONE));
        req.set(new OrdType(OrdType.MARKET));
        req.set(new TimeInForce(TimeInForce.FILL_OR_KILL));
        req.set(new TransactTime(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()));
        req.set(new Account(account));
        return req;
    }

    @Override
    public void fromApp(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        messageCracker.crack(message, sessionId);
    }
}
