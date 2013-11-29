package com.artifex.mupdfdemo;

public class ChoosePDFItem {
	enum Type {
            PARENT, DIR, DOC, NEW
	}

	final public Type type;
	final public String name;

	public ChoosePDFItem (Type t, String n) {
		type = t;
		name = n;
	}
}
