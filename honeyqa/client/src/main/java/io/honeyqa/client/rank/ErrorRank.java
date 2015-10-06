package io.honeyqa.client.rank;

public enum ErrorRank {

    Unhandle(0), // Unhandled error
    Native(1), // Native error
    Critical(2), // Critical error
    Major(3), // Major error
    Minor(4), // Minor error
    ;

    private final int value;

    ErrorRank(int value) {
        this.value = value;
    }

    /**
     * @return error rank
     */
    public int value() {
        return value;
    }
}
