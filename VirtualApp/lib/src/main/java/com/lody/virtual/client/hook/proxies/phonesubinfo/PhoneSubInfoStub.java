package com.lody.virtual.client.hook.proxies.phonesubinfo;

import com.lody.virtual.client.hook.base.BinderInvocationProxy;
import com.lody.virtual.client.hook.base.Inject;
import com.lody.virtual.client.hook.base.ReplaceCallingPkgMethodProxy;
import com.lody.virtual.client.hook.base.ReplaceLastPkgMethodProxy;
import com.lody.virtual.client.ipc.VLocationManager;

import java.lang.reflect.Method;

import mirror.com.android.internal.telephony.IPhoneSubInfo;

/**
 * @author Lody
 */
@Inject(MethodProxies.class)
public class PhoneSubInfoStub extends BinderInvocationProxy {
	public PhoneSubInfoStub() {
		super(IPhoneSubInfo.Stub.asInterface, "iphonesubinfo");
	}

	@Override
	protected void onBindMethods() {
		super.onBindMethods();
		addMethodProxy(new ReplaceLastPkgMethodProxy("getNaiForSubscriber"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getImeiForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getDeviceSvn"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getDeviceSvnUsingSubId"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getSubscriberId"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getSubscriberIdForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getGroupIdLevel1"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getGroupIdLevel1ForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getLine1Number"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getLine1NumberForSubscriber"){
			@Override
			public Object call(Object who, Method method, Object... args) throws Throwable {
				if(VLocationManager.get().hasVirtualLocation(getAppUserId())){
					return null;
				}
				return super.call(who, method, args);
			}
		});
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getLine1AlphaTag"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getLine1AlphaTagForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getMsisdn"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getMsisdnForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getVoiceMailNumber"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getVoiceMailNumberForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getVoiceMailAlphaTag"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getVoiceMailAlphaTagForSubscriber"));
		addMethodProxy(new ReplaceCallingPkgMethodProxy("getIccSerialNumber"));
		addMethodProxy(new ReplaceLastPkgMethodProxy("getIccSerialNumberForSubscriber"){
			@Override
			public Object call(Object who, Method method, Object... args) throws Throwable {
				if(VLocationManager.get().hasVirtualLocation(getAppUserId())){
					return null;
				}
				return super.call(who, method, args);
			}
		});
	}

}
