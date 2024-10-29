package tukano.impl.data;

public class LikesDAO extends Likes {
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

	public LikesDAO() {
	}
	
	
	@Override
	public String toString() {
		return "LikesDAO [_rid=" + _rid + ", _ts=" + _ts + ", Likes=" + super.toString();
	}
}
