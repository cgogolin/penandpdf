package com.cgogolin.penandpdf;

public class ChoosePDFItem {
    enum Type {PARENT, DIR, DOC, NEW}

    final public Type type;
    final public String name;

    public ChoosePDFItem (Type type, String name) {
        this.type = type;
        this.name = name;
    }
}
