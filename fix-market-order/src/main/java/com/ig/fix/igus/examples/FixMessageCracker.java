package com.ig.fix.igus.examples;

import lombok.extern.slf4j.Slf4j;
import quickfix.FieldNotFound;
import quickfix.IncorrectTagValue;
import quickfix.MessageCracker;
import quickfix.SessionID;
import quickfix.UnsupportedMessageType;
import quickfix.fix50sp2.ExecutionReport;

@Slf4j
public class FixMessageCracker extends MessageCracker {

    public void onMessage(ExecutionReport message, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
      log.info("received an executionReport: {}",message);  
    }
}
