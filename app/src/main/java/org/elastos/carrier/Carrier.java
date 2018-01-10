package org.elastos.carrier;

import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.util.List;
import java.util.ArrayList;

import org.elastos.carrier.exceptions.ElastosException;

/**
 * The class representing Carrier node instance.
 */
public class Carrier {
	/**
	 * Max Carrier ID length.
	 */
	public static final int MAX_ID_LEN = 45;

	/**
	 * Max Carrier KEY length.
	 */
	public static final int MAX_KEY_LEN = 45;

	private static final String TAG = "CarrierCore";
	private static Carrier carrier;
	private Thread carrierThread;
	private CarrierHandler handler;
	private long nativeCookie = 0;  // store the native (JNI-layered) carrier handler
	private boolean didKill = false;

	static {
		System.loadLibrary("carrierjni");
	}

	private static class Callbacks {
		private List<FriendInfo> friends;

		void onIdle(Carrier carrier) {
			carrier.handler.onIdle(carrier);
		}

		void onConnection(Carrier carrier, ConnectionStatus status) {
			carrier.handler.onConnection(carrier, status);
		}

		void onReady(Carrier carrier) {
			carrier.handler.onReady(carrier);
		}

		void onSelfInfoChanged(Carrier carrier, UserInfo userInfo) {
			carrier.handler.onSelfInfoChanged(carrier, userInfo);
		}

		boolean onFriendsIterated(Carrier carrier, FriendInfo info) {
			if (friends == null)
				friends = new ArrayList<FriendInfo>();

			if (info != null) {
				friends.add(info);
			} else {
				carrier.handler.onFriends(carrier, friends);
				friends = null;
			}
			return true;
		}

		void onFriendConnection(Carrier carrier, String friendid, ConnectionStatus status) {
			carrier.handler.onFriendConnection(carrier, friendid, status);
		}

		void onFriendInfoChanged(Carrier carrier, String friendId, FriendInfo info) {
			carrier.handler.onFriendInfoChanged(carrier, friendId, info);
		}

		void onFriendPresence(Carrier carrier, String friendId, PresenceStatus presence) {
			carrier.handler.onFriendPresence(carrier, friendId, presence);
		}

		void onFriendRequest(Carrier carrier, String userId, UserInfo info, String hello) {
			carrier.handler.onFriendRequest(carrier, userId, info, hello);
		}

		void onFriendAdded(Carrier carrier, FriendInfo friendInfo) {
			carrier.handler.onFriendAdded(carrier, friendInfo);
		}

		void onFriendRemoved(Carrier carrier, String friendId) {
			carrier.handler.onFriendRemoved(carrier, friendId);
		}

		void onFriendMessage(Carrier carrier, String from, String message) {
			carrier.handler.onFriendMessage(carrier, from, message);
		}

		void onFriendInviteRequest(Carrier carrier, String from, String data) {
			carrier.handler.onFriendInviteRequest(carrier, from, data);
		}
	}

	/**
	 * Options defines several settings that control the way the Carrier node
	 * connects to an carrier network.
	 *
	 * Default values are not defined for bootstraps of Options, so application
	 * should be set bootstraps clearly.
	 */
	public static class Options {
		private String persistentLocation;
		private boolean udpEnabled;
		private List<BootstrapNode> bootstrapNodes;

		public static class BootstrapNode {
			private String ipv4;
			private String ipv6;
			private String port;
			private String address;

			public BootstrapNode setIpv4(String ipv4) {
				this.ipv4 = ipv4;
				return this;
			}

			public String getIpv4() {
				return ipv4;
			}

			public BootstrapNode setIpv6(String ipv6) {
				this.ipv6 = ipv6;
				return this;
			}

			public String getIpv6() {
				return ipv6;
			}

			public BootstrapNode setPort(String port) {
				this.port = port;
				return this;
			}

			public String getPort() {
				return port;
			}

			public BootstrapNode setAddress(String address) {
				this.address = address;
				return this;
			}

			public String getAddress() {
				return address;
			}
		}

		/**
		 * Set the persistent data location.
		 * <p>
		 * The location must be set.
		 *
		 * @param persistentLocation The persistent data location to set
		 * @return The current Options object reference.
		 */
		public Options setPersistentLocation(String persistentLocation) {
			this.persistentLocation = persistentLocation;
			return this;
		}

		/**
		 * Get the persistent data location.
		 *
		 * @return The persistent data location
		 */
		public String getPersistentLocation() {
			return persistentLocation;
		}

		/**
		 * Set to use udp transport or not.
		 * Setting this value to false will force carrier node to TCP only, which will
		 * potentially slow down the message to run through.
		 */
		public Options setUdpEnabled(boolean udpEnabled) {
			this.udpEnabled = udpEnabled;
			return this;
		}

		/**
		 * Get the udp transport used or not.
		 */
		public boolean getUdpEnabled() {
			return udpEnabled;
		}

		public Options setBootstrapNodes(List<BootstrapNode> bootstrapNodes) {
			this.bootstrapNodes = bootstrapNodes;
			return this;
		}

		public List<BootstrapNode> getBootstrapNodes() {
			return bootstrapNodes;
		}
	}

	// native jni methods.
	private native boolean native_init(Options options, Callbacks callbacks);
	private native boolean native_run(int interval);
	private native void  native_kill();

	private native String get_address();
	private native String get_node_id();

	private native boolean set_nospam(byte[] nospam);
	private native byte[] get_nospam();

	private native boolean set_self_info(UserInfo uerInfo);
	private native UserInfo get_self_info();

	private native boolean set_presence(PresenceStatus status);
	private native PresenceStatus get_presence();

	private native boolean is_ready();

	private native boolean get_friends(FriendsIterator iterator, Object context);
	private native FriendInfo get_friend(String userId);
	private native boolean label_friend(String userId, String label);
	private native boolean is_friend(String userId);

	private native boolean add_friend(String userid, String hello);
	private native boolean accept_friend(String userId);
	private native boolean remove_friend(String userId);

	private native boolean send_message(String to, String message);
	private native boolean friend_invite(String to, String data,
										 FriendInviteResponseHandler handler);
	private native boolean reply_friend_invite(String from, int status, String reason,
											   String data);
	private static native int get_error_code();

	private Carrier(CarrierHandler handler) {
		this.handler = handler;
	}

	/**
	 * Get current version of Carrier node.
	 *
	 * @return
	 * 		The version of carrier node.
	 */
	public static String getVersion() {
		return "5.0/Android";
	}

	/**
	 * Check if the ID is Carrier node id.
	 *
	 * @param
	 * 		id		The carrier node id to be check.
	 *
	 * @return
	 * 		True if id is valid, otherwise false.
	 */
	public static boolean isValidId(String id) {
		try {
			return Base58.decode(id).length == 32;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Check if the carrier node address is valid.
	 *
	 * @param
	 * 		address			The carrier node address to be check.
	 *
	 * @return
	 * 		True if key is valid, otherwise false.
	 */
	public static boolean isValidAddress(String address) {
		try {
			return Base58.decode(address).length == 32;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Get a carrier node singleton instance. After getting the instance with first time,
	 * it's ready to start and therefore connect to carrier network.
	 *
	 * @param
	 * 		options		The options to set for creating carrier node.
	 * @param
	 * 		handler		The interface handler for carrier node.
	 *
	 * @return
	 * 		A carrier node instance
	 *
	 * @throws
	 * 		ElastosException
	 */
	public static Carrier getInstance(Options options, CarrierHandler handler) throws ElastosException {
		if (options == null || handler == null)
				throw new IllegalArgumentException();

		if (carrier == null) {
			Callbacks callbacks = new Callbacks();
			Carrier tmp = new Carrier(handler);

			if (!tmp.native_init(options, callbacks))
				throw new ElastosException(get_error_code());

			Log.i(TAG, "Carrier node instance created");
			carrier = tmp;
  		}
		return carrier;
	}

	@Override
	protected void finalize() throws Throwable {
		kill();
		super.finalize();
	}

	/**
	 * Get a carrier node singleton instance.
	 *
	 * @return
	 * 		A carrier node instance or nil on failure.
	 */
	public static Carrier getInstance() {
		return carrier;
	}

	/**
	 * Start carrier node asynchronously to connect to carrier network. If the connection
	 * to network is successful, carrier node starts working.
	 *
	 * @param
	 * 		iterateInterval		Internal loop interval, in milliseconds.
	 */
	public void start(final int iterateInterval) {
		if (carrierThread == null) {
			carrierThread = new Thread() {
				@Override
				public void run() {
					Log.i(TAG, "Native carrier node started");
					if (!carrier.native_run(iterateInterval)) {
						Log.e(TAG, "Native carrier node started error(" + get_error_code() + ")");
						return;
					}
					Log.i(TAG, "Native carrier node stoped");
				}
			};
			carrierThread.start();
		}
	}

	/**
	 * Disconnect carrier node from carrier network, and destroy all associated resources to
	 * carreier node instance.
	 *
	 * After calling the method, the carrier node instance becomes invalid.
	 */
	public synchronized void kill() {
		if (!didKill) {

			Log.i(TAG, "Killing Carrier node instance ...");
			native_kill();
			didKill = true;
			carrier = null;

			if (carrierThread != null) {
				try {
					carrierThread.join();
				} catch (Exception e) {
					Log.i(TAG, "Join carrier thread is interrupted");
				}
				carrierThread = null;
			}

			Log.i(TAG, "Carrier instance killed");
		}
	}

	/**
	 * Get node address associated with the carrier node instance.
	 *
	 * @return
	 *  	the node address.
	 */
	public String getAddress() throws ElastosException {
		String address = get_address();
		if (address == null)
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current carrier address: " + address);
		return address;
	}

	/**
	 * Get node identifier associated with the carrier node instance.
	 *
	 * @return
	 * 		the node identifier
	 */
	public String getNodeId() throws ElastosException {
		String nodeId = get_node_id();
		if (nodeId == null)
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current carrier NodeId: " + nodeId);
		return nodeId;
	}

	/**
	 * Get user identifier associated with the carrier node instance.
     *
	 * @return
	 * 		the user identifier
	 */
	public String getUserId() throws ElastosException {
		String userId = getNodeId();
		Log.d(TAG, "Current carrier userId: " + userId);
		return userId;
	}

	/**
	 * Update self nospam for Carrier node address.
	 *
	 * Update the 4-byte nospam part of the Carrier address with host byte order
	 * expected. Nospam for Carrier address is used to eliminate spam friend
	 * request.
	 *
	 * @param
	 * 		nospam 			An 4-bytes integer value.
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void setNospam(byte[] nospam) throws ElastosException {
		if (nospam.length != 4)
			throw new IllegalArgumentException();

		if (!set_nospam(nospam))
			throw new ElastosException(get_error_code());
	}

	/**
	 * \~Egnlish
	 * Get the nospam for Carrier address.
	 *
	 * Get the 4-byte nospam part of the Carrier address with host byte order
	 * expected. Nospam for Carrier address is used to eliminate spam friend
	 * request.
	 *
	 * @throws
	 * 		ElastosException
	 */
	public byte[] getNospam() throws ElastosException {
		byte[] nospam = get_nospam();
		return nospam;
	}

	/**
	 * Update self user information.
	 *
	 * After self user information changed, carrier node will update this information
	 * to carrier network, and thereupon network broadcasts the change to all friends.
	 *
	 * @param
	 * 		newValue	The user information to update for the carrier node.
	 *
	 * @throws
	 * 		IllegalArgumentException
	 * 		ElastosException
	 */
	public void setSelfInfo(UserInfo newValue) throws ElastosException {
		if (newValue == null)
			throw new IllegalArgumentException();

		if (!set_self_info(newValue))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current user information updated");
	}

	/**
	 * Get self user information.
	 *
	 * @return
	 * 		the user information to the carrier node.
	 *
	 * @throws
	 * 		ElastosException
	 */

	public UserInfo getSelfInfo() throws ElastosException {
		UserInfo userInfo = get_self_info();
		if (userInfo == null)
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current user information: " + userInfo);
		return userInfo;
	}

	/**
	 * Update self presence status.
	 *
	 * @param
	 * 		status 			the new presence status.
	 *
	 * @throws
	 * 		ElastosException
	 */
	void setPresence(PresenceStatus status) throws ElastosException {
		if (status == null)
			throw new IllegalArgumentException();

		if (!set_presence(status))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current presence updated to be " + status);
	}

	/**
	 * Get self presence status.
	 *
	 * @throws
	 * 		ElastosException
	 */
	PresenceStatus getPresence() throws ElastosException {
		PresenceStatus status = get_presence();
		if (status == null)
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current presence " + status);
		return status;
	}

	/**
	 * \~English
	 * Check if carrier node instance is being ready.
	 *
	 * All carrier interactive APIs should be called only if carrier node instance
	 * is being ready.
	 *
	 * @return
	 *      true if the carrier node instance is ready, or false if not.
	 *
	 */
	public boolean isReady() {
		return is_ready();
	}
	/**
	 * Get friends list.
	 *
	 * @return
	 * 		The list of friend information to current user
	 *
	 * @throws
	 * 		ElastosException
	 */
	public List<FriendInfo> getFriends() throws ElastosException {
		List<FriendInfo> friends = new ArrayList<FriendInfo>();

		boolean result = get_friends(new FriendsIterator() {
			public boolean onIterated(FriendInfo info, Object context) {
				List<FriendInfo> friends = (ArrayList<FriendInfo>)context;
				if (info != null)
					friends.add(info);
				return true;
			}
		}, friends);

		if (!result)
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Current user's friends listed below: +++++>>");
		for (FriendInfo friend: friends) {
			Log.d(TAG, friend.toString());
		}
		Log.d(TAG, "<<++++++++");

		return friends;
	}

	/**
	 * Get specified friend information.
	 *
	 * @param
	 * 		userId		The user identifier of friend
	 *
	 * @return
	 * 		The friend information.
	 *
	 * @throws
	 * 		ElastosException
	 */
	public FriendInfo getFriend(String userId) throws ElastosException {
		if (userId == null || userId.length() == 0)
			throw new IllegalArgumentException();

		FriendInfo friendInfo = get_friend(userId);
		if (friendInfo == null)
			throw new ElastosException(get_error_code());

		Log.d(TAG, "The information of friend " + userId + ": " + friendInfo);
		return friendInfo;
	}

	/**
	 * Set the label of the specified friend.
	 *
	 * The label of a friend is a private alias name for current user. It can be
	 * seen by current user only, and has no impact to the target friend itself.
	 *
	 * @param
	 * 		userId			The friend's user identifier
	 * @param
	 * 		label			The new label of specified friend
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void LabelFriend(String userId, String label) throws ElastosException {
		if (userId == null || userId.length() == 0 ||
			label  == null || label.length() == 0)
			throw new IllegalArgumentException();

		if (!label_friend(userId, label))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Label friend " + userId + " as  " + label);
	}

	/**
	 * Check if the user ID is friend.
	 *
	 * @param
	 * 		userId 		The userId to check
	 *
	 * @return
	 * 		True if the user is a friend, or false if not
	 *
	 * @throws
	 * 		ElastosException
	 */
	public boolean isFriend(String userId) throws ElastosException {
		if (userId == null || userId.length() == 0)
			throw new IllegalArgumentException();

		return is_friend(userId);
	}

	/**
	 * Attempt to add friend by sending a new friend request.
	 *
	 * This function will add a new friend with specific address, and then
	 * send a friend request to the target node.
	 *
	 * @param
	 * 		hello 		the target user id
	 * @param
	 * 		userId 	 	PIN for target user, or any application defined content
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void addFriend(String userId, String hello) throws ElastosException {
		if (userId == null || userId.length() == 0)
			throw new IllegalArgumentException();

		Log.d(TAG, "Attempt to make friend with user " + userId + " to send friend reques" +
				" with greeting hello " + hello);

		if (!add_friend(userId, hello))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Added friend " + userId);
	}

	/**
	 * Accept the friend request.
	 *
	 * This function is used to add a friend in response to a friend request.
	 *
	 * @param
	 * 		userId 		The user id who want be friend with current user
	 * @throws
	 * 		ElastosException
	 */
	public void AcceptFriend(String userId) throws ElastosException {
		if (userId == null || userId.length() == 0)
			throw new IllegalArgumentException();

		if (!accept_friend(userId))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Accepted friend requrest from " + userId);
	}

	/**
	 * Remove a friend.
	 *
	 * This function will send a remove friend indicator to carrier network.
	 *
	 * If all correct, the network will clean the friend relationship.
	 *
	 * @param
	 * 		userId	The target user id to remove friendship
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void removeFriend(String userId) throws ElastosException {
		if (userId == null || userId.length() == 0)
			throw new IllegalArgumentException();

		if (!remove_friend(userId))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Friend " + userId + " was removed");
	}

	/**
	 * Send a message to a friend.
	 *
	 * The message length may not exceed MAX_APP_MESSAGE_LEN, and message itself
	 * should be text-formatted. Larger messages must be split by application
	 * and sent as separate messages. Other nodes can reassemble the fragments.
     *
	 * @param
	 * 		to 			The target id
	 * @param
	 * 		message		The message content defined by application
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void sendFriendMessage(String to, String message) throws ElastosException {
		if (to == null || to.length() == 0 ||
				message == null | message.length() == 0)
			throw new IllegalArgumentException();

		if (!send_message(to, message))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Send message [" + message + "] to friend " + to);
	}

	/**
	 * Send invite request to a friend.
	 *
	 * Application can attach the application defined data with in the invite
	 * request, and the data will send to target friend.
	 *
	 * @param
	 * 		to			The target id
	 * @param
	 * 		data 		The application defined data send to target user
	 * @param
	 * 		handler	   	The handler to receive invite reponse
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void inviteFriend(String to, String data, FriendInviteResponseHandler handler)
			throws ElastosException {

		if (to == null || to.length() == 0 ||
				data == null || data.length() == 0 || handler == null)
			throw new IllegalArgumentException();

		Log.d(TAG, "Inviting friend " + to + "with greet data " + data);

		if (!friend_invite(to, data, handler))
			throw new ElastosException(get_error_code());

		Log.d(TAG, "Send friend invite request to " + to);
	}

	/**
	 * Reply the friend invite request.
	 *
	 * This function will send a invite response to friend.
	 *
	 * @param
	 * 		to			The id who send invite request
	 * @param
	 * 		status		The status code of the response. 0 is success, otherwise is error
	 * @param
	 * 		reason		The error message if status is error, or null if success
	 * @param
	 * 		data		The application defined data send to target user. If the status
	 * 	                is error, this will be ignored
	 *
	 * @throws
	 * 		ElastosException
	 */
	public void replyFriendInvite(String to, int status, String reason, String data)
			throws ElastosException {
		if (to == null || to.length() == 0 || (status != 0 && reason == null))
			throw new IllegalArgumentException("Bad arguments");

		if (status == 0)
			Log.d(TAG, String.format("Attempt to confirm friend invite to %s with data [%s]",
					to, data));
		else
			Log.d(TAG, String.format("Attempt to refuse friend invite to %s with status %d," +
					"and reason %s", to, status, reason));

		if (!reply_friend_invite(to, status, reason, data))
			throw new ElastosException(get_error_code());

		if (status == 0)
			Log.d(TAG, String.format("Confirmed friend invite to %s with data [%s]", to, data));
		else
			Log.d(TAG, String.format("Refused friend invite to %s with status %d and " +
					"reason %s", to, status, reason));
	}
}