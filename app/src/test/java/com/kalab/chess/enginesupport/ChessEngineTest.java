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
    private String packageName = "PackageName";
    private String fileName = "FileName";
    private String name = "ChessEngine";

    @Before
    public void setUp() throws Exception {
        testEngine = new ChessEngine(name, "FileName", "Authority", "PackageName", versionCode, "LicenseCheckActivity");
    }

    @After
    public void tearDown() throws Exception {
        testEngine = null;
    }

    @Test
    public void testGetVersionCode() throws Exception {
        try {
            assertEquals(versionCode, testEngine.getVersionCode());
            System.out.println("Test Passed!");
        } catch (Exception ex) {
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(ex.toString());
        }
    }

    @Test
    public void testGetPackageName() throws Exception {
        try {
            assertEquals(packageName, testEngine.getPackageName());     //Should Fail
            System.out.println("Test Passed!");
        } catch (Exception e) {
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    /*Duplicate Test, Commenting this one out temporarily.
    @Test
    public void testGetFileName() throws Exception {
        assertEquals("FileName", this.testEngine.getFileName());
        System.out.println("@Test - testGetFileName()");
    }*/

    @Test
    public void testGetFileName() throws Exception {
        try {
            assertEquals(fileName, testEngine.getFileName());
            System.out.println("Test Passed!");
        }
        catch (Exception e){
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    @Test
    public void testGetAuthority() throws Exception {
        try {
            assertEquals("Authority", testEngine.getAuthority());
            System.out.println("Test Passed!");
        }
        catch (Exception e){
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    @Test
    public void testGetURI() throws Exception {
        try {
            assertEquals("content://" + testEngine.getAuthority() + "/" + testEngine.getFileName(), testEngine.getUri());
            System.out.println("Test Passed!");
        } catch (Exception e) {
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    //Zach's Test
    @Test
    public void testGetName() throws Exception{
        try{
            assertEquals(name, testEngine.getName());
            System.out.println("Test Passed!");
        }
        catch (Exception e){
            System.out.println("Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

}