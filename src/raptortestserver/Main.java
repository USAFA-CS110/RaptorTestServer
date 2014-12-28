package raptortestserver;

import java.net.*;

/**
 * RAPTOR Test Server
 * To Run, Type "java -cp RaptorTestServer.jar raptortestserver.Main [PORT] [Test Case Path] [Max Timeout] [Verbose (T/F)]"
 * @author adrian.defreitas
 */
public class Main 
{
    /**
     * Runs the Program
     * @param args - Contains Command Line Arguments
     */
    public static void main(String[] args) 
    {
        int     port         = 0;
        int     timeout      = 0;
        String  testCasePath = "";
        boolean verboseOutput = false;
                
        if (args.length != 4)
        {
            System.out.println("Usage:  RaptorTestServer.jar [PORT] [TEST CASE PATH] [MAX OUTPUT] [VERBOSE]");
            System.out.println("  [PORT]           - The TCP Port the Server will Listen On (1024 - 65536)");
            System.out.println("  [TEST CASE PATH] - The Full Path to the Folder Containing Test Case XML Data");
            System.out.println("  [MAX TIMEOUT]    - The Amount of Time to Wait for Each Test (in milliseconds)");
            System.out.println("  [VERBOSE]        - (True/False) Triggers Verbose Output Mode");
        }
        else
        {
            try
            {
                // Reads in Command Line Arguments
                port          = Integer.parseInt(args[0]);
                testCasePath  = (args[1].endsWith("\\")) ? args[1] : args[1] + "\\";
                timeout       = Integer.parseInt(args[2]);
                verboseOutput = Boolean.parseBoolean(args[3]);

                // Outputs System Configuration
                System.out.println("RAPTOR Test Server v1.01");
                System.out.println("  Port:        " + port);
                System.out.println("  Test Cases:  " + testCasePath);
                System.out.println("  Max Timeout: " + timeout + "ms\n");

                // Creates a Server Socket that Listens on a Port
                ServerSocket wsSocket = new ServerSocket(port);

                // Continally Searches for New Connections
                while (true)
                {
                    TestServer grader = new TestServer(testCasePath, wsSocket.accept(), timeout, verboseOutput);
                    Thread thread = new Thread(grader);
                    thread.start();
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    }
}
