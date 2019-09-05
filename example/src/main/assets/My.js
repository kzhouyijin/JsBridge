
function My() {

    function sendToNative(key, message, obj) {
        //call native method
   console.log("2");
          console.log( window.WebViewJavascriptBridge);
        window.WebViewJavascriptBridge.callHandler(key, message, obj);
    }

    function buildBlueMessage(key, value) {
        var message = { "key": key, "value": value };
        return JSON.stringify(message);
    };

    this.Toast = function Toast(message,obj) {

        sendToNative("Toast", buildBlueMessage("1", message), obj);

    };
};

my = new My();
