package com.cgogolin.penandpdf;

public class LinkInfoExternal extends LinkInfo {
    final public String url;

    public LinkInfoExternal(float l, float t, float r, float b, String url) {
        super(l, t, r, b);
        this.url = url;
    }

    public void acceptVisitor(LinkInfoVisitor visitor) {
        visitor.visitExternal(this);
    }

    public LinkType type(){
        return LinkType.External;
    }
}
