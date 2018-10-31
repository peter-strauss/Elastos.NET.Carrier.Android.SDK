package org.elastos.carrier.session;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.elastos.carrier.AbstractCarrierHandler;
import org.elastos.carrier.Carrier;
import org.elastos.carrier.common.TestContext;
import org.elastos.carrier.common.TestOptions;
import org.elastos.carrier.exceptions.ElastosException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ManagerTest {
	private static String TAG = "SessionManagerTest";
	private static TestContext context = new TestContext();
	private static TestHandler handler = new TestHandler(context);
	private static Carrier carrier;

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
	}

	@Test
	public void testGetInstanceWithoutRequestHandler() {
		try {
			Manager sessionMgr = Manager.getInstance(carrier);
			assertNotNull(sessionMgr);
			sessionMgr.cleanup();
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testGetInstanceWithRequestHandler() {
		try {
			Manager sessionMgr = Manager.getInstance(carrier, new ManagerHandler() {
				@Override
				public void onSessionRequest(Carrier carrier, String from, String sdp) {
					Log.i(TAG, "onSessionRequest");
				}
			});
			assertNotNull(sessionMgr);
			sessionMgr.cleanup();
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@BeforeClass
	public static void setUp() {
		try {
			TestOptions options = new TestOptions(context.getAppPath());
			Carrier.initializeInstance(options, handler);
			carrier = Carrier.getInstance();
			carrier.start(0);
			synchronized (carrier) {
				carrier.wait();
			}
			Log.i(TAG, "Carrier node is ready now");

		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	@AfterClass
	public static void tearDown() {
		try {
			carrier.kill();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
