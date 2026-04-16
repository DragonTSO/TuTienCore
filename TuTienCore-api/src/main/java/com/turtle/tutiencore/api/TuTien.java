package com.turtle.tutiencore.api;

public class TuTien {
    
    private static TuTienAPI api;

    public static TuTienAPI getApi() {
        return api;
    }

    public static void setApi(TuTienAPI api) {
        if (TuTien.api != null) {
            throw new UnsupportedOperationException("Cannot redefine singleton TuTienAPI");
        }
        TuTien.api = api;
    }
}
