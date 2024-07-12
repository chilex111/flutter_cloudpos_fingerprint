import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.lightBlue,
      ),
      home: const MyHomePage(title: 'ConvertDiffFinger'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final MethodChannel _platform = const MethodChannel('com.example.cloudpos_fingerprint/cloudpos_fingerprint');
  final List<Text> _hints = [];
  final ScrollController _scrollController = ScrollController();

  Future<void> _checkDeviceType() async {
    final String result = await _platform.invokeMethod('checkDeviceType');
    _setHintMessage("deviceType is $result", 0);
  }

  void _setHintMessage(String hint, int code){
    Color textColor = Colors.black;
    if (code == 1) {
      textColor = Colors.blue;
    } else if (code == 2) {
      textColor = Colors.red;
    }
    setState(() {
      _hints.add(Text( hint,style: TextStyle(color: textColor)));
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());
  }

  void _clickBtn() {
    _setHintMessage("Please put your finger !", 0);
    _platform.invokeMethod('sendData');
  }
  @override
  void initState() {
    super.initState();
    _platform.setMethodCallHandler(_handleMethodCall);
    _checkDeviceType();
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'getDataNomal':
      case 'getDataSuccess':
      case 'getDataFail':
        final int code = call.method == 'getDataSuccess' ? 1 : call.method == 'getDataFail' ? 2 : 0;
        _setHintMessage(call.arguments, code);
        break;
      default:
        throw MissingPluginException();
    }
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Column(
        children: <Widget>[
          ElevatedButton(
            onPressed: _clickBtn,
            child: const Text('Read'),
          ),
          Expanded(
            child: SizedBox(
              child: ListView.builder(
                controller: _scrollController,
                itemCount: _hints.length,
                itemBuilder: (context, index) {
                  return _hints[index];
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}
