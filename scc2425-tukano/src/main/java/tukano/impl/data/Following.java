package tukano.impl.data;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name ="PostgreFollowing")
public class Following{

	@Id 
	String follower;
	@JsonProperty("id")
	String id;
	@Id 
	String followee;

	public Following(String follower, String followee) {
		super();
		this.follower = follower;
		this.followee = followee;
		this.id = followee + '|'+ follower;
	}

	public Following() {

	}

	public String getFollower() {
		return follower;
	}
	public String getid() {
		return id;
	}
	public void setFollower(String follower) {
		this.follower = follower;
	}

	public String getFollowee() {
		return followee;
	}

	public void setFollowee(String followee) {
		this.followee = followee;
	}

	@Override
	public int hashCode() {
		return Objects.hash(followee, follower);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Following other = (Following) obj;
		return Objects.equals(followee, other.followee) && Objects.equals(follower, other.follower);
	}

	@Override
	public String toString() {
		return "Following [follower=" + follower + ", followee=" + followee + "]";
	}
	
	
}