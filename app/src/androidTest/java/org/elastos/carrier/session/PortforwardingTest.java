package org.elastos.carrier.session;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.elastos.carrier.AbstractCarrierHandler;
import org.elastos.carrier.Carrier;
import org.elastos.carrier.ConnectionStatus;
import org.elastos.carrier.FriendInfo;
import org.elastos.carrier.common.RobotConnector;
import org.elastos.carrier.common.TestContext;
import org.elastos.carrier.common.TestHelper;
import org.elastos.carrier.common.TestHelper.ITestChannelExecutor;
import org.elastos.carrier.common.TestOptions;
import org.elastos.carrier.exceptions.ElastosException;
import org.elastos.carrier.robot.Robot;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class PortforwardingTest {
	private static final String TAG = "PortforwardingTest";
	private static TestContext context = new TestContext();
	private static TestHandler handler = new TestHandler(context);
	private static final SessionManagerHandler sessionHandler = new SessionManagerHandler();
	private static RobotConnector robot;
	private static Carrier carrier;
	private static Manager sessionManager;
	private static final TestStreamHandler streamHandler = new TestStreamHandler();
	private static Session session;
	private static Stream stream;
	private static final String service = "test_portforwarding_service";
	private static final int port = 20172;
	private static int shadowPort = 20173;
	private static ServerSocket localServer;
	private static Socket localClient;
	private static final String localIP = getLocalIpAddress();

	static class TestHandler extends AbstractCarrierHandler {
		private TestContext mContext;

		TestHandler(TestContext context) {
			mContext = context;
		}

		@Override
		public void onReady(Carrier carrier) {
			synchronized (carrier) {
				carrier.notify();
			}
		}

		@Override
		public void onFriendConnection(Carrier carrier, String friendId, ConnectionStatus status) {
			TestContext.Bundle bundle = mContext.getExtra();
			bundle.setRobotOnline(status == ConnectionStatus.Connected);
			bundle.setRobotConnectionStatus(status);
			bundle.setFrom(friendId);

			Log.d(TAG, "Robot connection status changed -> " + status.toString());
			synchronized (this) {
				notify();
			}
		}

		@Override
		public void onFriendAdded(Carrier carrier, FriendInfo info) {
			Log.d(TAG, String.format("Friend %s added", info.getUserId()));
			synchronized (this) {
				notify();
			}
		}

		@Override
		public void onFriendRemoved(Carrier carrier, String friendId) {
			Log.d(TAG, String.format("Friend %s removed", friendId));
			synchronized (this) {
				notify();
			}
		}
	}

	static class SessionManagerHandler implements ManagerHandler {
		@Override
		public void onSessionRequest(Carrier carrier, String from, String sdp) {
			LocalData data = (LocalData)context.getExtra().getExtraData();
			if (data == null) {
				data = new LocalData();
				context.getExtra().setExtraData(data);
			}
			data.mRequestReceived = true;
			data.mSdp = sdp;

			Log.d(TAG, String.format("Session Request from %s", from));
			synchronized (sessionHandler) {
				sessionHandler.notify();
			}
		}
	}

	static class LocalData {
		public String mSdp = null;
		public boolean mRequestReceived = false;
		public StreamState mState;
		public int mCompleteStatus = 0;
	}

	static class TestStreamHandler implements StreamHandler {
		@Override
		public void onStateChanged(Stream stream, StreamState state) {
			LocalData data = (LocalData)context.getExtra().getExtraData();
			if (data == null) {
				data = new LocalData();
				context.getExtra().setExtraData(data);
			}
			data.mState = state;

			Log.d(TAG, "onStateChanged state="+state);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onStreamData(Stream stream, byte[] data) {
			Log.d(TAG, "onStreamData data="+(new String(data)));
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public boolean onChannelOpen(Stream stream, int channel, String cookie) {
			Log.d(TAG, "onChannelOpen cookie="+cookie);
			synchronized (this) {
				this.notify();
			}
			return true;
		}

		@Override
		public void onChannelOpened(Stream stream, int channel) {
			Log.d(TAG, "onChannelOpened channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onChannelClose(Stream stream, int channel, CloseReason reason) {
			Log.d(TAG, "onChannelClose channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public boolean onChannelData(Stream stream, int channel, byte[] data) {
			Log.d(TAG, "onChannelData channel="+channel+", data="+(new String(data)));
			synchronized (this) {
				this.notify();
			}
			return true;
		}

		@Override
		public void onChannelPending(Stream stream, int channel) {
			Log.d(TAG, "onChannelPending channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onChannelResume(Stream stream, int channel) {
			Log.d(TAG, "onChannelResume channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}
	}

	static class TestSessionRequestCompleteHandler implements SessionRequestCompleteHandler {
		@Override
		public void onCompletion(Session session, int status, String reason, String sdp) {
			Log.d(TAG, String.format("Session complete, status: %d, reason: %s", status, reason));
			LocalData data = (LocalData)context.getExtra().getExtraData();
			data.mCompleteStatus = status;

			if (status == 0) {
				try {
					session.start(sdp);
				}
				catch (ElastosException e) {
					e.printStackTrace();
					assertTrue(false);
				}
			}

			synchronized (this) {
				notify();
			}
		}
	}

	public void testStreamScheme(StreamType stream_type, int stream_options, ITestChannelExecutor channelExecutor)
	{
		assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
		try {
			assertTrue(carrier.isFriend(robot.getNodeid()));
			assertTrue(robot.writeCmd("sinit"));

			String[] args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("sinit", args[0]);
			assertEquals("success", args[1]);

			session = sessionManager.newSession(robot.getNodeid());
			assertTrue(session != null);

			stream = session.addStream(stream_type, stream_options, streamHandler);
			assertNotNull(stream);

			//Stream initialized
			synchronized (streamHandler) {
				streamHandler.wait();
			}

			LocalData data = (LocalData)context.getExtra().getExtraData();
			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.Initialized));

			TestSessionRequestCompleteHandler completeHandler = new TestSessionRequestCompleteHandler();
			session.request(completeHandler);

			//Stream initialized
			synchronized (streamHandler) {
				streamHandler.wait(1000);
			}
			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.TransportReady));

			args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("srequest", args[0]);
			assertEquals("received", args[1]);

			assertTrue(robot.writeCmd(String.format("sreply confirm %d %d", stream_type.value(), stream_options)));

			//Stream initialized
			synchronized (completeHandler) {
				completeHandler.wait();
			}

			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mCompleteStatus == 0);

			args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("sreply", args[0]);
			assertEquals("success", args[1]);

			//Stream connecting
			synchronized (streamHandler) {
				streamHandler.wait(1000);
			}

			data = (LocalData)context.getExtra().getExtraData();
			if ((!data.mState.equals(StreamState.Connecting)) && (!data.mState.equals(StreamState.Connected))) {
				// if error, consume ctrl acknowlege from robot.
				args = robot.readAck();
				assertEquals("sconnect", args[0]);
				assertEquals("failed", args[1]);
			}

			// Stream 'connecting' state is a transient state.
			assertTrue(data.mState == StreamState.Connecting || data.mState == StreamState.Connected);
			args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("sconnect", args[0]);
			assertEquals("success", args[1]);

			//Stream connected
			synchronized (streamHandler) {
				streamHandler.wait(1000);
			}

			assertTrue(data.mState.equals(StreamState.Connected));

			if (channelExecutor != null) {
				channelExecutor.executor();
			}

			session.removeStream(stream);
			session.close();

			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.Closed));

			robot.writeCmd("sfree");
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void serverThreadBody(final LocalPortforwardingData ctxt) {
		try {
			ctxt.returnValue = -1;
			localServer = new ServerSocket(port, 10, InetAddress.getByName(localIP));
			Log.d(TAG, "server begin to receive data:");

			Socket client = localServer.accept();
			DataInputStream reader = new DataInputStream(client.getInputStream());
			String msg;
			while (true) {
				try {
					msg = reader.readUTF();
					Log.d(TAG, "recv msg="+msg);
					ctxt.recvCount += msg.getBytes().length;
				}
				catch (EOFException e) {
					break;
				}
			}

			ctxt.recvCount /= 1024;
			Log.d(TAG, String.format("finished receiving %d Kbytes data, closed by remote peer.",
					ctxt.recvCount));
			ctxt.returnValue = 0;
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if (localServer != null) {
					localServer.close();
					localServer = null;
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void clientThreadBody(final LocalPortforwardingData ctxt) {
		try {
			localClient = new Socket(ctxt.ip, ctxt.port);
			char[] data = new char[1024];
			for (int i = 0; i < 1024; i++) {
				data[i] = 'D';
			}

			ctxt.returnValue = -1;

			Thread.sleep(500);

			Log.d(TAG, "client begin to send data:");

			DataOutputStream writer = new DataOutputStream(localClient.getOutputStream());
			for (int i = 0; i < ctxt.sentCount; i++) {
				writer.writeUTF(new String(data));
			}

			Log.d(TAG, String.format("finished sending %d Kbytes data", ctxt.sentCount));
			Log.d(TAG, "client send data in success");

			ctxt.returnValue = 0;
		}
		catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if (localClient != null) {
					localClient.close();
					localClient = null;
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class LocalPortforwardingData {
		private String ip;
		private int port = 0;
		private int recvCount = 0;
		private int sentCount = 0;
		private int returnValue = -1;
	}

	class TestPortforwardingExecutor implements ITestChannelExecutor {
		@Override
		public void executor() {
			int pfid = 0;
			try {
				assertTrue(robot.writeCmd(String.format("spfsvcadd %s tcp %s %s", service, Robot.ROBOTHOST, port)));

				String[] args = robot.readAck();
				assertTrue(args != null && args.length == 2);
				assertEquals("spfsvcadd", args[0]);
				assertEquals("success", args[1]);

				pfid = stream.openPortForwarding(service, PortForwardingProtocol.TCP, localIP, Integer.toString(++shadowPort));

				if (pfid > 0) {
					Log.d(TAG, "Open portforwarding successfully");
				}
				else {
					Log.d(TAG, String.format("Open portforwarding failed (0x%x)", pfid));
				}

				assertTrue(robot.writeCmd(String.format("spfsvcrun %s %s", Robot.ROBOTHOST, port)));
				args = robot.readAck();
				assertTrue(args != null && args.length == 2);
				assertEquals("spfsvcrun", args[0]);
				assertEquals("success", args[1]);

				final LocalPortforwardingData client_ctxt = new LocalPortforwardingData();
				client_ctxt.ip = localIP;
				client_ctxt.port = shadowPort;
				client_ctxt.recvCount = 0;
				client_ctxt.sentCount = 1024;
				client_ctxt.returnValue = -1;

				Thread clientThread = new Thread(new Runnable() {
					@Override
					public void run() {
						clientThreadBody(client_ctxt);
					}
				});
				clientThread.start();

				try {
					clientThread.join();
					assertTrue(client_ctxt.returnValue != -1);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}

				args = robot.readAck();
				assertTrue(args != null && args.length == 3);
				assertEquals("spfsvcrun", args[0]);
				assertEquals("0", args[1]);
				assertEquals("1024", args[2]);

				assertTrue(robot.writeCmd(String.format("spfsvcremove %s", service)));
			}
			catch (ElastosException e) {
				Log.d(TAG, "Open portforwarding failed: " + Integer.toHexString(e.getErrorCode()));
				e.printStackTrace();
				fail();
			}
			finally {
				if (pfid > 0) {
					try {
						stream.closePortForwarding(pfid);
					}
					catch (ElastosException e) {
						Log.d(TAG, "Close portforwarding failed: " + Integer.toHexString(e.getErrorCode()));
						e.printStackTrace();
						fail();
					}
				}
			}
		}
	}

	class TestReversedPortforwardingExecutor implements ITestChannelExecutor {
		@Override
		public void executor() {
			try {
				final LocalPortforwardingData server_ctxt = new LocalPortforwardingData();
				server_ctxt.ip = localIP;
				server_ctxt.port = port;
				server_ctxt.recvCount = 0;
				server_ctxt.sentCount = 0;
				server_ctxt.returnValue = -1;

				Thread serverThread = new Thread(new Runnable() {
					@Override
					public void run() {
						serverThreadBody(server_ctxt);
					}
				});
				serverThread.start();

				session.addService(service, PortForwardingProtocol.TCP, localIP, Integer.toString(port));

				assertTrue(robot.writeCmd(String.format("spfopen %s tcp %s %s", service, Robot.ROBOTHOST, (++shadowPort))));

				String[] args = robot.readAck();
				assertTrue(args != null && args.length == 2);
				assertEquals("spfopen", args[0]);
				assertEquals("success", args[1]);

				assertTrue(robot.writeCmd(String.format("spfsenddata %s %s\n", Robot.ROBOTHOST, shadowPort)));

				args = robot.readAck();
				assertTrue(args != null && args.length == 3);
				assertEquals("spfsenddata", args[0]);
				assertEquals("0", args[1]);
				assertEquals("1024", args[2]);

				serverThread.join();
				assertTrue(server_ctxt.returnValue != -1);
				assertEquals(1024, server_ctxt.recvCount);

				assertTrue(robot.writeCmd("spfclose"));

				args = robot.readAck();
				assertTrue(args != null && args.length == 2);
				assertEquals("spfclose", args[0]);
				assertEquals("success", args[1]);

				session.removeService(service);
			}
			catch (ElastosException | InterruptedException e) {
				e.printStackTrace();
				fail();
			}
		}
	}

	public static String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
						return inetAddress.getHostAddress();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("IpAddress", ex.toString());
		}

		return null;
	}

	private void portforwardingImpl(int stream_options)
	{
		TestPortforwardingExecutor executor = new TestPortforwardingExecutor();
		testStreamScheme(StreamType.Text, stream_options, executor);
	}

	private void reversedPortforwardingImpl(int stream_options)
	{
		TestReversedPortforwardingExecutor executor = new TestReversedPortforwardingExecutor();
		testStreamScheme(StreamType.Text, stream_options, executor);
	}

	@Test
	public void testSessionPortforwardingReliable() {
		int stream_options = 0;
		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_PORT_FORWARDING;

		portforwardingImpl(stream_options);
	}

	@Test
	public void testSessionPortforwardingReliablePlain() {
		int stream_options = 0;
		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_PLAIN;
		stream_options |= Stream.PROPERTY_PORT_FORWARDING;

		portforwardingImpl(stream_options);
	}

	@Test
	public void testSessionReversedPortforwardingReliable() {
		int stream_options = 0;
		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_PORT_FORWARDING;

		reversedPortforwardingImpl(stream_options);
	}

	@Test
	public void testSessionReversedPortforwardingReliablePlain() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_PLAIN;
		stream_options |= Stream.PROPERTY_PORT_FORWARDING;

		reversedPortforwardingImpl(stream_options);
	}

	@BeforeClass
	public static void setUp() {
		robot = RobotConnector.getInstance();
		TestOptions options = new TestOptions(context.getAppPath());
		if (!robot.connectToRobot()) {
			android.util.Log.e(TAG, "Connection to robot failed, abort this test");
			fail();
		}
		try {
			Carrier.initializeInstance(options, handler);
			carrier = Carrier.getInstance();
			carrier.start(0);
			synchronized (carrier) {
				carrier.wait();
			}
			Log.i(TAG, "Carrier node is ready now");

			sessionManager = Manager.getInstance(carrier, sessionHandler);
			assertTrue(sessionManager != null);
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			Log.e(TAG, "Carrier node start failed, abort this test.");
		}
	}

	@AfterClass
	public static void tearDown() {
		carrier.kill();
		sessionManager.cleanup();
		robot.disconnect();
	}
}
