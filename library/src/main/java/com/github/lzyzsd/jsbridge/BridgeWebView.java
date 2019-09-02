package com.github.lzyzsd.jsbridge;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("SetJavaScriptEnabled")
public class BridgeWebView extends WebView {

	private final String TAG = "BridgeWebView";

	public static final String toLoadJs = "WebViewJavascriptBridge.js";

	Map<String, FetchCallBack> responseCallbacks = new HashMap<String, FetchCallBack>();
	Map<String, BridgeHandler> messageHandlers = new HashMap<String, BridgeHandler>();
	BridgeHandler defaultHandler = new DefaultHandler();

	private List<Message> startupMessage = new ArrayList<Message>();

	public List<Message> getStartupMessage() {
		return startupMessage;
	}

	public void setStartupMessage(List<Message> startupMessage) {
		this.startupMessage = startupMessage;
	}

	private long uniqueId = 0;

	public BridgeWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public BridgeWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public BridgeWebView(Context context) {
		super(context);
		init();
	}

	/**
	 *
	 * @param handler
	 *            default handler,handle messages send by js without assigned handler name,
     *            if js message has handler name, it will be handled by named handlers registered by native
	 */
	public void setDefaultHandler(BridgeHandler handler) {
       this.defaultHandler = handler;
	}

    private void init() {
		this.setVerticalScrollBarEnabled(false);
		this.setHorizontalScrollBarEnabled(false);
		this.getSettings().setJavaScriptEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
		this.setWebViewClient(generateBridgeWebViewClient());
	}

    protected BridgeWebViewClient generateBridgeWebViewClient() {
        return new BridgeWebViewClient(this);
    }

    /**
     * 获取到CallBackFunction data执行调用并且从数据集移除
     * @param url
     */
	void handlerReturnData(String url) {
		String functionName = BridgeUtil.getFunctionFromReturnUrl(url);
		FetchCallBack f = responseCallbacks.get(functionName);
		String data = BridgeUtil.getDataFromReturnUrl(url);
		if (f != null) {
			f.onFetch(data);
			responseCallbacks.remove(functionName);
			return;
		}
	}



    /**
     * 保存message到消息队列
     * @param handlerName handlerName
     * @param data data
     * @param fetchCallBack CallBackFunction
     */
	private void doSend(String handlerName, String data, FetchCallBack fetchCallBack) {
		Message m = new Message();
		if (!TextUtils.isEmpty(data)) {
			m.setData(data);
		}
		if (fetchCallBack != null) {
			String callbackStr = String.format(BridgeUtil.CALLBACK_ID_FORMAT, ++uniqueId + (BridgeUtil.UNDERLINE_STR + SystemClock.currentThreadTimeMillis()));
			responseCallbacks.put(callbackStr, fetchCallBack);
			m.setCallbackId(callbackStr);
		}
		if (!TextUtils.isEmpty(handlerName)) {
			m.setHandlerName(handlerName);
		}
		queueMessage(m);
	}

    /**
     * list<message> != null 添加到消息集合否则分发消息
     * @param m Message
     */
	private void queueMessage(Message m) {
		if (startupMessage != null) {
			startupMessage.add(m);
		} else {
			dispatchMessage(m);
		}
	}

    /**
     * 分发message 必须在主线程才分发成功
     * @param m Message
     */
	void dispatchMessage(Message m) {
        String messageJson = m.toJson();
        //escape special characters for json string  为json字符串转义特殊字符
        messageJson = messageJson.replaceAll("(\\\\)([^utrn])", "\\\\\\\\$1$2");
        messageJson = messageJson.replaceAll("(?<=[^\\\\])(\")", "\\\\\"");
		messageJson = messageJson.replaceAll("(?<=[^\\\\])(\')", "\\\\\'");
		messageJson = messageJson.replaceAll("%7B", URLEncoder.encode("%7B"));
		messageJson = messageJson.replaceAll("%7D", URLEncoder.encode("%7D"));
		messageJson = messageJson.replaceAll("%22", URLEncoder.encode("%22"));
        String javascriptCommand = String.format(BridgeUtil.JS_HANDLE_MESSAGE_FROM_JAVA, messageJson);
        // 必须要找主线程才会将数据传递出去 --- 划重点
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            this.loadUrl(javascriptCommand);
        }
    }

    /**
     * 刷新消息队列
     */
	void flushMessageQueue() {
		if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
			//WebView加载js方法
			loadUrl(BridgeUtil.JS_FETCH_QUEUE_FROM_JAVA, new FetchCallBack() {

				@Override
				public void onFetch(String data) {
					// deserializeMessage 反序列化消息
					Log.e("flushMessageQueue","data==="+data);
					List<Message> list = null;
					try {
						list = Message.toArrayList(data);
					} catch (Exception e) {
                        e.printStackTrace();
						return;
					}
					if (list == null || list.size() == 0) {
						return;
					}
					for (int i = 0; i < list.size(); i++) {
						Message m = list.get(i);
						String responseId = m.getResponseId();
						Log.e("flushMessageQueue","responseId==="+responseId);
						// 是否是response  CallBackFunction
						CallBackFunction responseFunction = null;
						// if had callbackId 如果有回调Id
						final String callbackId = m.getCallbackId();
						Log.e("flushMessageQueue","callbackId==="+callbackId);
						if (!TextUtils.isEmpty(callbackId)) {
							responseFunction = new CallBackFunction() {

                                @Override
                                public void onCallBackCompleted(String data) {
                                    Message responseMsg = new Message();
                                    responseMsg.setResponseId(callbackId);
                                    responseMsg.setResponseData(data);
                                    responseMsg.setMethodId(Message.METHOD_COMPLETED);
                                    queueMessage(responseMsg);
                                }

                                @Override
                                public void onCallBackError(String data) {
                                    Message responseMsg = new Message();
                                    responseMsg.setResponseId(callbackId);
                                    responseMsg.setMethodId(Message.METHOD_FAIL);
                                    responseMsg.setResponseData(data);
                                    queueMessage(responseMsg);
                                }

                                @Override
                                public void onCallBackSuccess(String data) {
                                    Message responseMsg = new Message();
                                    responseMsg.setResponseId(callbackId);
                                    responseMsg.setMethodId(Message.METHOD_SUCCESS);
                                    responseMsg.setResponseData(data);
                                    queueMessage(responseMsg);
                                }


							};
						} else {

						    return;
//							responseFunction = new CallBackFunction() {
//								@Override
//								public void onCallBack(String data) {
//									// do nothing
//								}
//							};
						}
						// BridgeHandler执行
						BridgeHandler handler;
						if (!TextUtils.isEmpty(m.getHandlerName())) {
							handler = messageHandlers.get(m.getHandlerName());
						} else {
							handler = defaultHandler;
						}
						if (handler != null){
							handler.handler(m.getData(), responseFunction);
						}
					}

				}
			});
		}
	}


	/**
	 * 调用js方法，并记录js回调函数
	 * @param jsUrl
	 * @param fetchCallBack
	 */
	public void loadUrl(String jsUrl, FetchCallBack fetchCallBack) {
		this.loadUrl(jsUrl);
        // 添加至 Map<String, CallBackFunction>
		responseCallbacks.put(BridgeUtil.parseFunctionName(jsUrl), fetchCallBack);
	}

	/**
	 * register handler,so that javascript can call it
	 * 注册处理程序,以便javascript调用它
	 * @param handlerName handlerName  js代码发送的目标程序
	 * @param handler BridgeHandler   处理js发送过来的数据
	 */
	public void registerHandler(String handlerName, BridgeHandler handler) {
		if (handler != null) {
            // 添加至 Map<String, BridgeHandler>
			messageHandlers.put(handlerName, handler);
		}
	}

	/**
	 * unregister handler
	 *
	 * @param handlerName
	 */
	public void unregisterHandler(String handlerName) {
		if (handlerName != null) {
			messageHandlers.remove(handlerName);
		}
	}



}
