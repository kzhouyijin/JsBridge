package com.github.lzyzsd.jsbridge;

/**
 * 用于服务没有注册时调用，永远返回失败
 */
public class DefaultHandler implements BridgeHandler{

	String TAG = "DefaultHandler";
	
	@Override
	public void handler(String data, CallBackFunction function) {
		if(function != null){
			function.onCallBackError(data);
			function.onCallBackCompleted(data);
		}
	}

}
