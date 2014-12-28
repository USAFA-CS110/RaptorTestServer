package raptortestserver;

import java.net.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * A Threaded Test Server (NOT the Assessment Environment Version!)
 * @author adrian.defreitas
 */
public class TestServer implements Runnable
{
    // The Folder Containing all Test Case Files
    private String testFolderPath;
    private int    maxTimeout;
    
    // Connection Variables
    private Socket         clientSocket;
    private BufferedReader input;
    private OutputStream   output;
    
    // Flag Used to Determine if Verbose Output is Allowed
    private boolean verbose;
        
    /**
     * Constructor
     * @param testFolderPath - The folder containing all test case XML files
     * @param clientSocket   - The client connection socket
     * @param maxTimeout     - The maximum time to run each file (in milliseconds)
     * @param verbose        - Flag to trigger verbose output
     */
    public TestServer(String testFolderPath, Socket clientSocket, int maxTimeout, boolean verbose) 
    {
        // Saves Settings
        this.clientSocket   = clientSocket;
        this.testFolderPath = testFolderPath;
        this.maxTimeout     = maxTimeout;
        this.verbose        = verbose;
    }
    
    /**
     * Runs the Threaded Process
     */
    @Override
    public void run()
    {
        // The command from the RAPTOR client
        String command = "";
        
        try
        {
            input  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = clientSocket.getOutputStream();

            // Retrieves the Command from the RAPTOR Client
            command = input.readLine();

            // Outputs the Command Received from the Client
            print("COMMAND (" + new Date().toString() + "): " + command);

            // "Parses" Commands
            if (command.equals("DIRECTORY"))
            {
                // Retrieves the Current Directory
                send(getDirectory());
            }
            else
            {
                // Retrieves and Runs the Requested Test
                administerTest(command);
            }
        }
        catch (IOException e)
        {
            System.out.println("An error occurrect while executing command \"" + command + "\"");
        }
        
        // Attempts to Close the Connection
        try
        {
            closeConnection();
        }
        catch (IOException ioE)
        {
            ioE.printStackTrace();
        }
    }
    
    /**
     * Prints a strong to the console (if in verbose mode)
     * @param s 
     */
    private void print(String s)
    {
        if (verbose)
        {
            System.out.println(s);
        }
    }
    
    /**
     * Closes all Connections
     */
    public void closeConnection() throws IOException
    {
        print("Closing Connection . . . ");
        input.close();
        output.close();
        clientSocket.close();
        print("Done!\n\n");
    }
    
    /**
     * Sends a Message Back to the RAPTOR Client
     */
    public void send(String message) throws IOException
    {
        // Creates a Buffer and Populates it with the Message
        byte[] sendBuff = new byte[message.length()];
        sendBuff = message.getBytes();
        
        // Sends the Message on the Stream
        output.write(sendBuff, 0, sendBuff.length);
    }
    
    /**
     * Retrieves the Auto Grader's Test Directory
     */
    public String getDirectory()
    {
        String directory = "";
                
        // Grabs the Folder Containing all the Tests
        File testDirectory = new File(testFolderPath);
        
        // Retrieves a List of All Files in the Directory
        String[] tests = testDirectory.list();
        
        // Appends the Test Files to the Directory
        for (int i=0; i<tests.length; i++)
        {
            // Only Allows XML Files
            if (tests[i].endsWith(".xml"))
            {
                // Grabs the Name of the File Without the .xml Extension
                String testName = tests[i].substring(0, tests[i].length()-4);

                // Concatenates the Directory Contents to the List
                directory += testName + "\r\n";
            }        
        }
        
        // Appends the EOF
        directory += "EOF\r\n";
        
        // Returns the Directory
        return directory;
    }
    
    /**
     * Retrieves and Runs a Test
     */
    public void administerTest(String testName) throws IOException
    {               
        try
        {
            // Strips off the .rap name (supports .rap.done files)
            if (testName.toLowerCase().endsWith(".rap"))
            {
                testName = testName.substring(0, testName.length()-4);
            }
            
            // Attempts to Open the File
            File testFile = new File(testFolderPath + testName + ".xml");
            
            if (testFile.isFile())
            {
                Document doc      = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(testFile);
                doc.getDocumentElement().normalize();

                // Grabs all Question Elements
                NodeList testLst = doc.getElementsByTagName("TestCase");

                // Debug Statement
                print(testLst.getLength() + " test cases found for this problem.");

                // Tells the Client RAPTOR Application How Many Tests Exist
                send(testLst.getLength() + "\r\n");

                // Administers Each Test
                for (int i=0; i<testLst.getLength(); i++)
                {
                    // Gets the Test Case
                    Element testCase = (Element)testLst.item(i);

                    // Grabs the Input/Output
                    NodeList inputLst  = testCase.getElementsByTagName("input");
                    NodeList outputLst = testCase.getElementsByTagName("output");
                    int expected = outputLst.getLength();
                    
                    //create a string list from the nodelist
                    List<String> outputList = new ArrayList<String>();
                    for(int j = 0; j < outputLst.getLength(); j++)
                    {
                       Element outputElement  = (Element)outputLst.item(j);
                       String  expectedOutput = outputElement.getChildNodes().item(0).getNodeValue(); 
                       outputList.add(expectedOutput);
                    }
                    
                    print("Test Case " + i + ":  " + inputLst.getLength() + " inputs; " + outputLst.getLength() + " outputs");
                    
                    // Sends a String Containing all Inputs and Sends it to the Client
                    for (int j=0; j<inputLst.getLength(); j++)
                    {
                        // Grabs the Input XML Element
                        Element inputElement = (Element)inputLst.item(j);

                        // Sends the Input Value to the Client
                        send(inputElement.getChildNodes().item(0).getNodeValue() + "\r\n");
                    }

                    // The EOF signifies that the test case description is complete
                    send("EOF\r\n");

                    // Saves the Current Raptor Response
                    boolean testResult     = true;
                    String  raptorResponse = "";

                    // Tracks the Number of Responses Received
                    int numResponses = 0;

                    // Tracks the number of correct responses
                    int numCorrect = 0;
                    
                    // Tracks the Start Time for the Test
                    Date startTime = new Date();

                    // Waits for a Response from the Client
                    while (true)
                    {
                        if (input.ready())
                        {                       
                            // Only retrieves the next input if the stream is ready
                            raptorResponse = input.readLine();  

                            if (raptorResponse.length() > 0 && !raptorResponse.contains("EOF"))
                            {
                                if (numResponses < expected)
                                {
                                    //print("  OUTPUT:  " + raptorResponse + " (compared to expected answer \"" + outputList.get(numResponses) + "\")");

                                    // try to remove the current output from the output list.  if successful, the string was found
                                    if(outputList.remove(raptorResponse))
                                    {
                                        numCorrect++;
                                    }
                                    else
                                    {
                                        // try to find the result another way
                                        boolean found = false;
                                        for(int j = 0; j < outputList.size(); j++)
                                        {
                                            String s = outputList.get(j);
                                            // try case insensitive comparison
                                            if(s.equalsIgnoreCase(raptorResponse))
                                            {
                                                found = true;
                                                // remove the matched output
                                                outputList.remove(j);
                                                j = outputList.size();
                                                numCorrect++;
                                            }
                                            else
                                            {
                                                // tokenize the string and see if the correct output matches one of the tokens
                                                String[] stringArray = raptorResponse.split(" ");
                                                if(stringArray.length >= 2)
                                                {
                                                    for(int k = 0; k < stringArray.length; k++)
                                                    {
                                                        if(stringArray[k].equalsIgnoreCase(s))
                                                        {
                                                            found = true;
                                                            k = stringArray.length;
                                                            // remove the matched output
                                                            outputList.remove(j);
                                                            j = outputList.size();
                                                            numCorrect++;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if(!found)
                                        {
                                            testResult = false;
                                        }
                                    }
                                    numResponses++;
                                }
                            }
                        }

                        // Exits Loop if Timeout or Received "EOF" Message
                        if (raptorResponse.equals("EOF") || (new Date().getTime() - startTime.getTime() > maxTimeout))
                        {
                            break;
                        } 
                        if (raptorResponse.equals("EOF"))
                        {
                            break;
                        }
                    }               

                    // A Test is Considered to Passed if
                    //  1.  All Test Outputs Match the Expected Outputs
                    //  2.  If the Number of Outputs Mathes the Expected Number of Outputs
                    System.out.println("  Expected " + expected + " response(s).  Received " + numResponses);
                    testResult = (testResult && numResponses == expected);

                    if (testResult)
                    {
                        print("  RESULT:  PASS");
                        send("CORRECT\r\n");        
                    }
                    else if(numCorrect > 0)
                    {
                        print("  RESULT:  Fail, " + numCorrect + "/" + expected + " outputs correct");
                        send("INCORRECT, " + numCorrect + "/" + expected + " outputs correct\r\n");
                    }
                    else
                    {
                        print("  RESULT:  FAIL");
                        send("INCORRECT\r\n");
                    }
                }
            }
            else
            {
                print("No test case found for " + testName);
                send("INVALID COMMAND OR ASSIGNMENT\r\n");    
            }
        }
        catch (Exception e)
        {
            System.out.println("Uh Oh.  The AutoGrader Crashed");
            
            // If there is any problem with the test, go ahead and deliver an error to the client
            send("INVALID COMMAND OR ASSIGNMENT\r\n");
        }
    }    
}
