#JsBridge

-----

inspired and modified from [this](https://github.com/jacin1/JsBridge) and wechat jsBridge file, with some bugs fix and feature enhancement.

This project make a bridge between Java and JavaScript.

It provides safe and convenient way to call Java code from js and call js code from java.

## 



## Use it in Java

add com.github.lzyzsd.jsbridge.BridgeWebView to your layout, it is inherited from WebView.

### Register a Java handler function so that js can call

```java

	webView.registerHandler("Toast", new BridgeHandler() {

			@Override
			public void handler(String data, CallBackFunction function) {
				Toast.makeText(MainActivity.this,"收到H5请求数据："+data,Toast.LENGTH_SHORT).show();

			//	function.onCallBackError("");
			//	function.onCallBackSuccess("调用onError方法:"+data);
				function.onCallBackCompleted("app返回h5参数:"+data);

			}

		});

```

js can call this Java handler method  by My.js

```javascript

   function My() {

    function sendToNative(key, message, obj) {
        //call native method

        window.WebViewJavascriptBridge.callHandler(key, message, obj);

    }

    function buildBlueMessage(key, value) {
        var message = { "key": key, "value": value };
        return JSON.stringify(message);
    };


    this.Toast = function Toast(message,obj) {

        sendToNative("Toast", buildBlueMessage("1", message), obj);

    };


    
}

my = new My();
```

then you can use my.js to call Java

```java

    my.Toast("你好", {
            success: (res) => {
                alert("success:"+res);
            },
            fail: (res) => {
            alert("fail:"+res);
            },
            complete: (res) => {
             alert("complete:"+res);
            }
        });
```

```javascript


```

### Register a JavaScript handler function so that Java can call

```javascript

    WebViewJavascriptBridge.registerHandler("functionInJs", function(data, responseCallback) {
        document.getElementById("show").innerHTML = ("data from Java: = " + data);
        var responseData = "Javascript Says Right back aka!";
        responseCallback(responseData);
    });

```



## License

This project is licensed under the terms of the MIT license.
