package com.ig.fix.igus.examples;

import quickfix.ApplicationAdapter;
import quickfix.MessageCracker;

public class FixApplication extends ApplicationAdapter {

	private MessageCracker messageCracker;

	public FixApplication(MessageCracker messageCracker) {
		this.messageCracker = messageCracker;
	}

}
