package com.continuuity.gateway.tools;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.utils.Copyright;
import com.continuuity.gateway.accessor.RestAccessor;
import com.continuuity.gateway.util.Util;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * This is a command line tool to interact with the key/vaue store of
 * data fabric. It can get a value by key, save a value for a key,
 * delete a key, or list all keys in the store.
 * <ul>
 * <li>It attempts to be smart and determine the URL of the REST
 * accessor auto-magically. If that fails, the user can give hints
 * via the --connector and --base arguments</li>
 * <li>The key can be read in binary form from a file, or provided
 * on the command line as a String in various encodings, as indicated
 * by the --hex, --url, and --encoding arguments</li>
 * <li>The value can be saved to a file in binary form, or printed
 * to the screen in the same encoding as the key.</li>
 * </ul>
 */
public class DataClient {

  /**
   * for debugging. should only be set to true in unit tests.
   * when true, program will print the stack trace after the usage.
   */
  public static boolean debug = false;

  boolean help = false;          // whether --help was there
  boolean verbose = false;       // for debug output
  String command = null;         // the command to run
  String baseUrl = null;         // the base url for HTTP requests
  String hostname = null;        // the hostname of the gateway
  String connector = null;       // the name of the rest accessor
  String key = null;             // the key to read/write/delete
  String value = null;           // the value to write
  String encoding = null;        // the encoding for --key and for display
                                 // of the value
  boolean hexEncoded = false;    // whether --key and display of value use
                                 // hexadecimal encoding
  boolean urlEncoded = false;    // whether --key and display of value use
                                 // url encoding
  boolean counter = false;       // whether --value should be interpreted as
                                 // a long counter
  String keyFile = null;         // the file to read the key from
  String valueFile = null;       // the file to read/write the value from/to
  int start = -1;                // the index to start the list from
  int limit = -1;                // the number of elements to list
  boolean clearAll = false;      // to clear everything
  boolean clearData = false;     // to clear all table data
  boolean clearQueues = false;   // to clear all event streams
  boolean clearStreams = false;  // to clear all intra-flow queues

  boolean keyNeeded;             // does the command require a key?
  boolean valueNeeded;           // does the command require a value?
  boolean outputNeeded;          // does the command require to write an
                                 // output value?

  /**
   * Print the usage statement and return null (or empty string if this is not
   * an error case). See getValue() for an explanation of the return type.
   *
   * @param error indicates whether this was invoked as the result of an error
   * @throws IllegalArgumentException in case of error, an empty string in case
   * of success
   */
  void usage(boolean error) {
    PrintStream out = (error ? System.err : System.out);
    String name = this.getClass().getSimpleName();
    Copyright.print(out);
    out.println("Usage: ");
    out.println("  " + name + " read --key <string> [ <options> ]");
    out.println("  " + name +
        " write --key <string> --value value [ <options> ]");
    out.println("  " + name + " delete --key <string> [ <options> ]");
    out.println("  " + name + " list [ <options> ]");
    out.println("  " + name +
        " clear ( --all | --data | --queues | --streams )");
    out.println("Additional options:");
    out.println("  --base <url>            " +
        "To specify the base url to send to");
    out.println("  --host <name>           " +
        "To specify the hostname to send to");
    out.println("  --connector <name>      " +
        "To specify the name of the rest connector");
    out.println("  --key <string>          To specify the key");
    out.println("  --key-file <path>       To read the binary key from a file");
    out.println("  --value <string>        To specify the value");
    out.println("  --value-file <path>     " +
        "To read/write the binary value from/to a file");
    out.println("  --hex                   " +
        "To use hexadecimal encoding for key and value");
    out.println("  --ascii                 " +
        "To use ASCII encoding for key and value");
    out.println("  --url                   " +
        "To use URL encoding for key and value");
    out.println("  --counter               " +
        "To interpret value as a long counter");
    out.println("  --start <n>             " +
        "To start at the nth element - only for list");
    out.println("  --limit <k>             " +
        "To list at most k elements - only for list");
    out.println("  --all                   To clear all data");
    out.println("  --data                  To clear all table data");
    out.println("  --streams               To clear all event streams");
    out.println("  --queues                To clear all intra-flow queues");
    out.println("  --encoding <name>       " +
        "To use this encoding for key and value");
    out.println("  --verbose               To see more verbose output");
    out.println("  --help                  To print this message");
    if (error) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Print an error message followed by the usage statement
   * @param errorMessage the error message
   */
  void usage(String errorMessage) {
    if (errorMessage != null) System.err.println("Error: " + errorMessage);
    usage(true);
  }

  /**
   * Parse the command line arguments
   */
  void parseArguments(String[] args) {
    if (args.length == 0) usage(true);
    if ("--help".equals(args[0])) {
      usage(false);
      help = true;
      return;
    } else {
      command = args[0];
    }
    // go through all the arguments
    for (int pos = 1; pos < args.length; pos++) {
      String arg = args[pos];
      if ("--base".equals(arg)) {
        if (++pos >= args.length) usage(true);
        baseUrl = args[pos];
      } else if ("--host".equals(arg)) {
        if (++pos >= args.length) usage(true);
        hostname = args[pos];
      } else if ("--connector".equals(arg)) {
        if (++pos >= args.length) usage(true);
        connector = args[pos];
      } else if ("--key".equals(arg)) {
        if (++pos >= args.length) usage(true);
        key = args[pos];
      } else if ("--value".equals(arg)) {
        if (++pos >= args.length) usage(true);
        value = args[pos];
      } else if ("--key-file".equals(arg)) {
        if (++pos >= args.length) usage(true);
        keyFile = args[pos];
      } else if ("--value-file".equals(arg)) {
        if (++pos >= args.length) usage(true);
        valueFile = args[pos];
      } else if ("--start".equals(arg)) {
        if (++pos >= args.length) usage(true);
        try {
          start = Integer.valueOf(args[pos]);
        } catch (NumberFormatException e) {
          usage(true);
        }
      } else if ("--limit".equals(arg)) {
        if (++pos >= args.length) usage(true);
        try {
          limit = Integer.valueOf(args[pos]);
        } catch (NumberFormatException e) {
          usage(true);
        }
      } else if ("--encoding".equals(arg)) {
        if (++pos >= args.length) usage(true);
        encoding = args[pos];
      } else if ("--ascii".equals(arg)) {
        encoding = "ASCII";
      } else if ("--url".equals(arg)) {
        urlEncoded = true;
      } else if ("--hex".equals(arg)) {
        hexEncoded = true;
      } else if ("--counter".equals(arg)) {
        counter = true;
      } else if ("--all".equals(arg)) {
        clearAll = true;
      } else if ("--data".equals(arg)) {
        clearData = true;
      } else if ("--queues".equals(arg)) {
        clearQueues = true;
      } else if ("--streams".equals(arg)) {
        clearStreams = true;
      } else if ("--verbose".equals(arg)) {
        verbose = true;
      } else if ("--help".equals(arg)) {
        help = true;
        usage(false);
        return;
      } else {  // unkown argument
        usage(true);
      }
    }
  }

  static List<String> supportedCommands =
      Arrays.asList("read", "write", "delete", "list", "clear");

  void validateArguments(String[] args) {
    // first parse command arguments
    parseArguments(args);
    if (help) return;
    // first validate the command
    if (!supportedCommands.contains(command))
      usage("Unsupported command '" + command + "'.");
    // verify that either --key or --key-file is given,
    // and same for --value and --value-file
    if (key != null && keyFile != null)
      usage("Only one of --key and --key-file may be specified");
    if (value != null && valueFile != null)
      usage("Only one of --value and --value-file may be specified");
    // verify that --limit or --start are only given for list command
    if (!"list".equals(command) && (start >= 0 || limit >= 0))
      usage("--start and --limit are only allowed for list");
    // verify that only one encoding was given
    int encodings = 0;
    keyNeeded = !(command.equals("list") || command.equals("clear"));
    valueNeeded = command.equals("write");
    outputNeeded = command.equals("read") || command.equals("list");
    boolean needsEncoding = (keyNeeded && keyFile == null)
        || ((valueNeeded || outputNeeded) && valueFile == null);
    if (hexEncoded) ++encodings;
    if (urlEncoded) ++encodings;
    if (encoding != null) ++encodings;
    if (needsEncoding && encodings > 1)
      usage("Only one encoding can be specified.");
    if (!needsEncoding && encodings > 0)
      usage("Encoding may not be specified for binary file.");
    // verify that only one hint is given for the URL
    if (hostname != null && baseUrl != null)
      usage("Only one of --host or --base may be specified.");
    if (connector != null && baseUrl != null)
      usage("Only one of --connector or --base may be specified.");
    // verify that a key is provided iff the command supports one
    if (keyNeeded) {
      if (key == null && keyFile == null)
        usage("A key must be specified for command " + command +
            " - use either --key or --key-file.");
    } else {
      if (key != null || keyFile != null)
        usage("A key may not be specified for command " + command + ".");
    }
    // verify that a value is provided iff the command supports one
    if (valueNeeded) {
      if (value == null && valueFile == null)
        usage("A value must be specified for command " + command +
            " - use either --value or --value-file.");
    } else {
      if ((value != null) || (!outputNeeded && valueFile != null))
        usage("A value may not be specified for command " + command + ".");
    }
    // verify that clear command specifies what to clear
    if ("clear".equals(command)) {
      if (!(clearAll || clearData || clearQueues || clearStreams))
        usage("You must specify what to clear - please us --all, --data, " +
            "--queues, and/or --streams.");
    }
    // --counter is only allowed for read and write, and not in conjunction
    // with --value-file
    if (counter) {
      if (!"read".equals(command) && !"write".equals(command))
        usage("--counter is only allowed for read and write.");
      if (valueFile != null)
        usage("Only one of --value-file and --counter may be specified.");
    }
  }

  /**
   * read the key using arguments
   */
  byte[] readKeyOrValue(String what, String str, String file) {
    byte[] binary;
    // is the key in a file?
    if (file != null) {
      binary = Util.readBinaryFile(keyFile);
      if (binary == null) {
        System.err.println("Cannot read " + what + " from file " + file + ".");
        return null;
    } }
    else if (counter && "value".equals(what)) {
      try {
        binary = Util.longToBytes(Long.valueOf(str));
      } catch (NumberFormatException e) {
        System.err.println(
            "Cannot parse '" + str + "' as long: " + e.getMessage());
        return null;
    } }
    // or is it in hexadecimal?
    else if (hexEncoded) {
      try {
        binary = Util.hexValue(str);
      } catch (NumberFormatException e) {
        System.err.println(
            "Cannot parse '" + str + "' as hexadecimal: " + e.getMessage());
        return null;
    } }
    // or is it in URL encoding?
    else if (urlEncoded) {
      binary = Util.urlDecode(str);
    }
    // lastly, it can be in the given encoding
    else if (encoding != null) {
      try {
        binary = str.getBytes(encoding);
      } catch (UnsupportedEncodingException e) {
        System.err.println("Unsupported encoding " + encoding);
        return null;
    } }
    // nothing specified, use default encoding
    else
      binary = str.getBytes();

    return binary;
  }

  /*
   * return the resulting value to the use, following arguments
   */
  String writeValue(byte[] binaryValue) {
    // was a file specified to write to?
    if (valueFile != null) {
      try {
        FileOutputStream out = new FileOutputStream(valueFile);
        out.write(binaryValue);
        out.close();
        if (verbose) System.out.println(binaryValue.length
            + " bytes written to file " + valueFile + ".");
        return binaryValue.length + " bytes written to file";
      } catch (IOException e) {
        System.err.println(
            "Error writing to file " + valueFile + ": " + e.getMessage());
        return null;
      } }
    if (counter)
      value = Long.toString(Util.bytesToLong(binaryValue));
    // was hex encoding requested?
    else if (hexEncoded)
      value = Util.toHex(binaryValue);
    // or was URl encoding specified?
    else if (urlEncoded)
      value = Util.urlEncode(binaryValue);
    // was a different encoding specified?
    else if (encoding != null) {
      try { // this may fail because encoding was user-specified
        value = new String(binaryValue, encoding);
      } catch (UnsupportedEncodingException e) {
        System.err.println("Unsupported encoding " + encoding);
        return null;
      }
    }
    // by default, assume the same encoding for the value as for the key
    else
      value = new String(binaryValue);

    if (verbose) System.out.println(
        "Value[" + binaryValue.length + " bytes]: " + value);
    else System.out.println(value);
    return value;
  }

  /*
   * return the resulting value to the use, following arguments
   */
  String writeList(byte[] binaryResponse) {
    if (binaryResponse.length == 0) {
      System.out.println("No results.");
      return "No results.";
    }
    // was a file specified to write to?
    if (valueFile != null) {
      try {
        FileOutputStream out = new FileOutputStream(valueFile);
        out.write(binaryResponse);
        out.close();
        if (verbose) System.out.println(binaryResponse.length +
            " bytes written to file " + valueFile + ".");
        return binaryResponse.length + " bytes written to file";
      } catch (IOException e) {
        System.err.println(
            "Error writing to file " + valueFile + ": " + e.getMessage());
        return null;
    } }
    else {
      try {
        System.out.write(binaryResponse);
        return binaryResponse.length + " bytes written to standard out.";
      } catch (IOException e) {
        System.err.println("Error writing to standard out: " + e.getMessage());
        return null;
      }
    }
  }

  /**
   * This is actually the main method, but in order to make it testable,
   * instead of exiting in case of error it returns null, whereas in case of
   * success it returns the retrieved value as shown
   * on the console.
   *
   * @param args   the command line arguments of the main method
   * @param config The configuration of the gateway
   * @return null in case of error, an string representing the retrieved value
   * in case of success
   */
  public String execute0(String[] args, CConfiguration config) {
    // parse and validate arguments
    validateArguments(args);
    if (help) return "";

    // determine the base url for the GET request
    if (baseUrl == null) baseUrl =
        Util.findBaseUrl(config, RestAccessor.class, connector, hostname);
    if (baseUrl == null) {
      System.err.println("Can't figure out the URL to send to. " +
          "Please use --base or --connector to specify.");
      return null;
    } else {
      if (verbose) System.out.println("Using base URL: " + baseUrl);
    }

    String urlEncodedKey = null;
    if (keyNeeded) {
      urlEncodedKey = Util.urlEncode(readKeyOrValue("key", key, keyFile));
      if (urlEncodedKey == null) return null;
    }
    byte[] binaryValue = null;
    if (valueNeeded) {
      binaryValue = readKeyOrValue("value", value, valueFile);
      if (binaryValue == null) return null;
    }

    // construct the full URL and verify its well-formedness
    String requestUrl = baseUrl + "default";
    if (keyNeeded) requestUrl += "/" + urlEncodedKey;
    if (verbose && !"list".equals(command))
      System.out.println("Request URI is: " + requestUrl);
    URI uri;
    try {
      uri = URI.create(requestUrl);
    } catch (IllegalArgumentException e) {
      // this can only happen if the --host, or --base are not valid for a URL
      System.err.println("Invalid request URI '" + requestUrl
          + "'. Check the validity of --host or --base arguments.");
      return null;
    }

    // prepare for HTTP
    HttpClient client = new DefaultHttpClient();
    HttpResponse response;

    // the rest depends on the command
    if ("read".equals(command)) {
      try {
        response = client.execute(new HttpGet(uri));
        client.getConnectionManager().shutdown();
      } catch (IOException e) {
        System.err.println("Error sending HTTP request: " + e.getMessage());
        return null;
      }
      if (!checkHttpStatus(response)) return null;
      // read the binary value from the HTTP response
      binaryValue = Util.readHttpResponse(response);
      if (binaryValue == null) return null;
      // now make returned value available to user
      return writeValue(binaryValue);
    }
    else if ("write".equals(command)) {
      try {
        HttpPut put = new HttpPut(uri);
        put.setEntity(new ByteArrayEntity(binaryValue));
        response = client.execute(put);
        client.getConnectionManager().shutdown();
      } catch (IOException e) {
        System.err.println("Error sending HTTP request: " + e.getMessage());
        return null;
      }
      if (!checkHttpStatus(response)) return null;
      return "OK.";
    }
    else if ("delete".equals(command)) {
      try {
        response = client.execute(new HttpDelete(uri));
        client.getConnectionManager().shutdown();
      } catch (IOException e) {
        System.err.println("Error sending HTTP request: " + e.getMessage());
        return null;
      }
      if (!checkHttpStatus(response)) return null;
      return "OK.";
    }
    else if ("list".equals(command)) {
      // we have to massage the URL a little more
      //String enc = urlEncoded ? "url" : hexEncoded ? "hex" : encoding;
      requestUrl += "?q=list";
      if (urlEncoded) requestUrl += "&enc=url";
      else if (hexEncoded) requestUrl += "&enc=hex";
      else if (encoding != null) requestUrl += "&enc=" + encoding;
      else requestUrl += "&enc=" + Charset.defaultCharset().displayName();
      if (start > 0) requestUrl += "&start=" + start;
      if (limit > 0) requestUrl += "&limit=" + limit;
      if (verbose) System.out.println("Request URI is: " + requestUrl);
      try {
        uri = URI.create(requestUrl);
      } catch (IllegalArgumentException e) {
        // this can only happen if the --host, or --base are not valid for a URL
        System.err.println("Invalid request URI '" + requestUrl
            + "'. Check the validity of --host or --base arguments.");
        return null;
      }
      // now execute this as a get
      try {
        response = client.execute(new HttpGet(uri));
        client.getConnectionManager().shutdown();
      } catch (IOException e) {
        System.err.println("Error sending HTTP request: " + e.getMessage());
        return null;
      }
      if (!checkHttpStatus(response)) return null;

      // read the binary value from the HTTP response
      binaryValue = Util.readHttpResponse(response);
      if (binaryValue == null) return null;
      // now make returned value available to user
      return writeList(binaryValue);
    }
    else if ("clear".equals(command)) {
      requestUrl = baseUrl + "?clear=";
      if (clearAll) requestUrl += "data,queues,streams";
      else if (clearData) requestUrl += "data";
      else if (clearQueues) requestUrl += "queues";
      else if (clearStreams) requestUrl += "streams";
      // now execute this as a get
      try {
        response = client.execute(new HttpPost(requestUrl));
        client.getConnectionManager().shutdown();
      } catch (IOException e) {
        System.err.println("Error sending HTTP request: " + e.getMessage());
        return null;
      }
      if (!checkHttpStatus(response)) return null;
      return "OK.";
    }
    return null;
  }

  /**
   * Check whether the Http return code is positive. If not, print the error
   * message and return false. Otherwise, if verbose is on, print the response
   * status line.
   * @param response the HTTP response
   * @return whether the response indicates success
   */
  boolean checkHttpStatus(HttpResponse response) {
    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
      if (verbose)
        System.out.println(response.getStatusLine());
      else
        System.err.println(response.getStatusLine().getReasonPhrase());
      return false;
    }
    if (verbose)
      System.out.println(response.getStatusLine());
    return true;
  }

  public String execute(String[] args, CConfiguration config) {
    try {
      return execute0(args, config);
    } catch (IllegalArgumentException e) {
      if (debug) { // this is mainly for debugging the unit test
        System.err.println("Exception for arguments: " +
            Arrays.toString(args) + ". Exception: " + e);
        e.printStackTrace(System.err);
      }
    }
    return null;
  }

  /**
    * This is the main method. It delegates to getValue() in order to make
    * it possible to test the return value.
    */
  public static void main(String[] args) {
    // create a config and load the gateway properties
    CConfiguration config = CConfiguration.create();
    // create a data client and run it with the given arguments
    DataClient instance = new DataClient();
    String value = instance.execute(args, config);
    // exit with error in case fails
    if (value == null) System.exit(1);
  }
}

