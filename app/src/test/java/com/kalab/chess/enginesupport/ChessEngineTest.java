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
            System.out.println("getVersionCode() Test Passed!");
        } catch (Exception ex) {
            System.out.println("getVersionCode() Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(ex.toString());
        }
    }

    @Test
    public void testGetPackageName() throws Exception {
        try {
            assertEquals(packageName, testEngine.getPackageName());     //Should Fail
            System.out.println("getPackageName() Test Passed!");
        } catch (Exception e) {
            System.out.println("getPackageName() Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    @Test
    public void testGetFileName() throws Exception {
        try {
            assertEquals(fileName, testEngine.getFileName());
            System.out.println("getFileName() Test Passed!");
        }
        catch (Exception e){
            System.out.println("getFileName() Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    @Test
    public void testGetAuthority() throws Exception {
        try {
            assertEquals("Authority", testEngine.getAuthority());
            System.out.println("getAuthority() Test Passed!");
        }
        catch (Exception e){
            System.out.println("getAuthority() Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    @Test
    public void testGetURI() throws Exception {
        try {
            assertEquals("content://" + testEngine.getAuthority() + "/" + testEngine.getFileName(), testEngine.getUri());
            System.out.println("testGetURI() Test Passed!");
        } catch (Exception e) {
            System.out.println("testGetURI() Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

    //Zach's Test
    @Test
    public void testGetName() throws Exception{
        try{
            assertEquals(name, testEngine.getName());
            System.out.println("getName() Test Passed!");
        }
        catch (Exception e){
            System.out.println("getName() Test Failed!");
            System.out.println("Exception Information:");
            System.out.println(e.toString());
        }
    }

}