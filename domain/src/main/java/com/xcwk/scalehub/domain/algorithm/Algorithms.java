package com.xcwk.scalehub.domain.algorithm;

/** 已注册的体成分算法。旧记录缺编号时仍是 builtin-1。 */
public final class Algorithms {
    private Algorithms() {
    }

    public static BodyComposition of(String id) {
        if (id == null || id.isEmpty() || BuiltinComposition.ID.equals(id)) {
            return BuiltinComposition.INSTANCE;
        }
        if (OkokBia.ID.equals(id)) {
            return OkokBia.INSTANCE;
        }
        return null;
    }
}
