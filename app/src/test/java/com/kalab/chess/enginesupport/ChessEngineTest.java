package com.kalab.chess.enginesupport;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by JC Snider on 2/4/2016.
 */
public class ChessEngineTest extends TestCase {

    private ChessEngine testEngine;
    private int versionCode = 2;
    private String packageName = "Package";
    private String fileName = "FileName";

    @Before
    public void setUp() throws Exception {
        testEngine = new ChessEngine("ChessEngine","FileName","Authority","PackageName",versionCode,"LicenseCheckActivity");
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testGetVersionCode() throws Exception {
        try{
            assertEquals(versionCode,testEngine.getVersionCode());
            System.out.println("Test Passed!");
        }
        catch (Exception ex) {
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(ex.toString());
        }
    }

    @Test
    public void testGetPackageName() throws Exception {
        try{
            assertEquals(packageName, testEngine.getPackageName());     //Should Fail
            System.out.println("Test Passed!");
        }
        catch (Exception e){
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    @Test
    public void testGetFileName() throws Exception {
        try {
            assertEquals(filename, testEngine.getFileName());
            System.out.println("Test Passed!");
        }
        catch (Exception e){
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }
}