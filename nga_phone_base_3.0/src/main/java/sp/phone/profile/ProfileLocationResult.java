package sp.phone.profile;

/** No raw response or account material crosses the supplemental read boundary. */
public final class ProfileLocationResult {

    public enum Kind { SUCCESS, FAILURE, RATE_LIMIT, SESSION_REJECTED }

    public final Kind kind;
    public final String location;
    public final long retryAt;

    private ProfileLocationResult(Kind kind, String location, long retryAt) {
        this.kind = kind;
        this.location = location;
        this.retryAt = retryAt;
    }

    public static ProfileLocationResult success(String location) {
        return new ProfileLocationResult(Kind.SUCCESS, location, 0);
    }

    public static ProfileLocationResult failure() {
        return new ProfileLocationResult(Kind.FAILURE, null, 0);
    }

    public static ProfileLocationResult rejected() {
        return new ProfileLocationResult(Kind.SESSION_REJECTED, null, 0);
    }

    public static ProfileLocationResult rateLimit(long retryAt) {
        return new ProfileLocationResult(Kind.RATE_LIMIT, null, retryAt);
    }
}
