package org.glassfish.el.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import javax.el.*;

/**
 *
 * @author Dongbin Nie
 */
public class OverloadedMethodTest {

    ELProcessor elp;

    @Before
    public void setUp() {
        elp = new ELProcessor();
        
        elp.defineBean("foo", new MyBean());
        
        elp.defineBean("i1", new I1Impl());
        elp.defineBean("i2", new I2Impl());
        elp.defineBean("i12", new I1AndI2Impl());
        elp.defineBean("i12s", new I1AndI2ImplSub());
        
    }

    @After
    public void tearDown() {
    }
    
    @Test
    public void testMethodWithNoArg() {
    assertEquals("methodWithNoArg", elp.eval("foo.methodWithNoArg()"));
    }
    
    @Test
    public void testMethodNotExisted() {
    try {
    elp.eval("foo.methodNotExisted()");
    fail("testNoExistedMethod Failed");
    } catch (MethodNotFoundException e) {
    }
    }
    
    @Test
    public void testMethodWithSingleArg() {
    assertEquals("I1", elp.eval("foo.methodWithSingleArg(i1)"));
    assertEquals("I2Impl", elp.eval("foo.methodWithSingleArg(i2)"));
    assertEquals("I1AndI2Impl", elp.eval("foo.methodWithSingleArg(i12)"));
    }
    
    @Test
    public void testMethodWithDoubleArgs() {
    assertEquals("I1Impl, I2", elp.eval("foo.methodWithDoubleArgs(i1, i2)"));
    assertEquals("I1, I2", elp.eval("foo.methodWithDoubleArgs(i12, i2)"));
    assertEquals("I1AndI2Impl, I1AndI2Impl", elp.eval("foo.methodWithDoubleArgs(i12, i12)"));
    assertEquals("I1AndI2Impl, I1AndI2Impl", elp.eval("foo.methodWithDoubleArgs(i12s, i12)"));
    assertEquals("I1AndI2Impl, I1AndI2Impl", elp.eval("foo.methodWithDoubleArgs(i12s, i12s)"));
    }
    
    @Test
    public void testMethodWithAmbiguousArgs() {
    assertEquals("I1AndI2Impl, I2", elp.eval("foo.methodWithAmbiguousArgs(i12, i2)"));
    assertEquals("I1, I1AndI2Impl", elp.eval("foo.methodWithAmbiguousArgs(i1, i12)"));
    try {
    elp.eval("foo.methodWithAmbiguousArgs(i12, i12)");
    fail("testMethodWithAmbiguousArgs Failed");
    } catch (MethodNotFoundException e) {
    }
    }
    
    @Test
    public void testMethodWithCoercibleArgs() {
    assertEquals("String, String", elp.eval("foo.methodWithCoercibleArgs('foo', 'bar')"));
    assertEquals("String, String", elp.eval("foo.methodWithCoercibleArgs(i1, i12)"));
    
    assertEquals("String, String", elp.eval("foo.methodWithCoercibleArgs2(i1, 12345678)"));
    assertEquals("Integer, Integer", elp.eval("foo.methodWithCoercibleArgs2(12345678, 12345678)"));
    }
    
    @Test
    public void testMethodWithVarArgs() {
    assertEquals("I1, I1...", elp.eval("foo.methodWithVarArgs(i1)"));
    assertEquals("I1, I1...", elp.eval("foo.methodWithVarArgs(i1, i1)"));
    assertEquals("I1, I1...", elp.eval("foo.methodWithVarArgs(i12, i1, i12)"));
    
    assertEquals("I1, I1AndI2Impl...", elp.eval("foo.methodWithVarArgs2(i1)"));
    assertEquals("I1, I1AndI2Impl...", elp.eval("foo.methodWithVarArgs2(i12)"));
    assertEquals("I1, I1...", elp.eval("foo.methodWithVarArgs2(i1, i1)"));
    assertEquals("I1, I1AndI2Impl...", elp.eval("foo.methodWithVarArgs2(i1, i12)"));
    }
    
    @Test
    public void testMethodInStdout() {
        elp.defineBean("out", System.out);
        elp.eval("out.println('hello!')");
        elp.eval("out.println(12345678)");
    }
    
    
    public static interface I1 {
    
    }
    
    public static interface I2 {
    
    }
    
    public static class I1Impl implements I1 {
    
    }
    
    public static class I2Impl implements I2 {
    
    }
    
    public static class I1AndI2Impl implements I1, I2 {
    
    }
    
    public static class I1AndI2ImplSub extends I1AndI2Impl {
    
    }

    static public class MyBean {
    
    public String methodWithNoArg() {
    return "methodWithNoArg";
    }
    
    public String methodWithSingleArg(I1 i1) {
    return "I1";
    }
    
    public String methodWithSingleArg(I2 i2) {
    return "I2";
    }
    
    public String methodWithSingleArg(I2Impl i2) {
    return "I2Impl";
    }
    
    public String methodWithSingleArg(I1AndI2Impl i1) {
    return "I1AndI2Impl";
    }
    
    public String methodWithDoubleArgs(I1 i1, I2 i2) {
    return "I1, I2";
    }
    
    public String methodWithDoubleArgs(I1Impl i1, I2 i2) {
    return "I1Impl, I2";
    }
    
    public String methodWithDoubleArgs(I1AndI2Impl i1, I1AndI2Impl i2) {
    return "I1AndI2Impl, I1AndI2Impl";
    }
    
    public String methodWithAmbiguousArgs(I1AndI2Impl i1, I2 i2) {
    return "I1AndI2Impl, I2";
    }
    
    public String methodWithAmbiguousArgs(I1 i1, I1AndI2Impl i2) {
    return "I1, I1AndI2Impl";
    }
    
    public String methodWithCoercibleArgs(String s1, String s2) {
    return "String, String";
    }
    
    public String methodWithCoercibleArgs2(String s1, String s2) {
    return "String, String";
    }
    
    public String methodWithCoercibleArgs2(Integer s1, Integer s2) {
    return "Integer, Integer";
    }
    
    public String methodWithVarArgs(I1 i1, I1... i2) {
    return "I1, I1...";
    }
    
    public String methodWithVarArgs2(I1 i1, I1... i2) {
    return "I1, I1...";
    }
    
    public String methodWithVarArgs2(I1 i1, I1AndI2Impl... i2) {
    return "I1, I1AndI2Impl...";
    }
    }
    
}