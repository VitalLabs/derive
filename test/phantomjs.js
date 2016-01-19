var system = require('system');
var url,args;

if (phantom.version.major > 1) {
  args = system.args;
  if (args.length < 2) {
    system.stderr.write('Expected a target URL parameter.');
    phantom.exit(1);
  }
  url = args[1];
} else {
  args = phantom.args;
  if (args.length < 1) {
    system.stderr.write('Expected a target URL parameter.');
    phantom.exit(1);
  }
  url = args[0];
}

var page = require('webpage').create();

page.onConsoleMessage = function (message) {
  console.log("Console: " + message);
};

console.log("Loading URL: " + url);

page.open(url, function (status) {
    if (status != "success") {
		console.log('Failed to open ' + url);
		phantom.exit(1);
    }
    console.log('Opened ' + url);

    var result = page.evaluate(function () {
		return  derive.runner.run_all_tests();
    });

    // NOTE: PhantomJS 1.4.0 has a bug that prevents the exit codes
    //        below from being returned properly. :(
    //
    // http://code.google.com/p/phantomjs/issues/detail?id=294

    if ( result ) {
		console.log("PhantomJS runner: Success.");
		phantom.exit(0);
    } else {
		console.log("PhantomJS runner: *** Tests failed! ***");
		phantom.exit(1);
    }
});
