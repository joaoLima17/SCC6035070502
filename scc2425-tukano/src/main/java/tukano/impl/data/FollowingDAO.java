package tukano.impl.data;

public class FollowingDAO extends Following {
    private String _rid;
	private String _ts;
	public String get_rid() {
		return _rid;
	}


	public void set_rid(String _rid) {
		this._rid = _rid;
	}


	public String get_ts() {
		return _ts;
	}


	public void set_ts(String _ts) {
		this._ts = _ts;
	}

	public FollowingDAO() {
	}
	
	
	@Override
	public String toString() {
		return "FollowingDAO [_rid=" + _rid + ", _ts=" + _ts + ", Following=" + super.toString();
	}
}