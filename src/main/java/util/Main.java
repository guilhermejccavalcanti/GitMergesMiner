package util;

import java.io.PrintStream;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 *
 * @author nstephen
 */
public class Main {

	public static void main(String[] args) throws Exception {

		// initialize logging to go to rolling log file
		LogManager logManager = LogManager.getLogManager();
		logManager.reset();

		// log file max size 10K, 3 rolling files, append-on-open
		Handler fileHandler = new FileHandler("logerr", 10000000, 3, true);
		fileHandler.setFormatter(new SimpleFormatter());
		Logger.getLogger("").addHandler(fileHandler);        

		// preserve old stdout/stderr streams in case they might be useful
		PrintStream stdout = System.out;
		PrintStream stderr = System.err;

		// now rebind stdout/stderr to logger
		Logger logger;
		LoggingOutputStream los;

		logger = Logger.getLogger("stderr");
		los	= new LoggingOutputStream(logger, StdOutErrLevel.STDERR);
		System.setErr(new PrintStream(los, true));
	}

}
