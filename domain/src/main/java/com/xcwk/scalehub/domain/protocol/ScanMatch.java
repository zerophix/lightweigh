package com.xcwk.scalehub.domain.protocol;

/** 广播能被某个协议认出时的结果。 */
public final class ScanMatch {
    public final String protocolId;
    public final String summary;

    public ScanMatch(String protocolId, String summary) {
        this.protocolId = protocolId;
        this.summary = summary == null ? "" : summary;
    }
}
