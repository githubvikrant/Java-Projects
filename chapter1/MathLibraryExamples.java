public class MathLibraryExamples {
    public static void main(String[] args) {
        // 1. Absolute Value
        System.out.println("abs(-10.0): " + Math.abs(-10)); // 10

        // 2. Power Function
        System.out.println("pow(2, 3): " + Math.pow(2, 3)); // 8.0

        // 3. Square Root
        System.out.println("sqrt(16): " + Math.sqrt(16)); // 4.0

        // 4. Cube Root
        System.out.println("cbrt(27): " + Math.cbrt(27)); // 3.0

        // 5. Exponential Function (e^x)
        System.out.println("exp(1): " + Math.exp(1)); // 2.718...

        // 6. Natural Logarithm (ln)
        System.out.println("log(10): " + Math.log(10)); // 2.302...

        // 7. Log base 10
        System.out.println("log10(100): " + Math.log10(100)); // 2.0

        // 8. Maximum and Minimum
        System.out.println("max(5, 10): " + Math.max(5, 10)); // 10
        System.out.println("min(5, 10): " + Math.min(5, 10)); // 5

        // 9. Trigonometric Functions
        System.out.println("sin(PI/2): " + Math.sin(Math.PI / 2)); // 1.0
        System.out.println("cos(PI): " + Math.cos(Math.PI)); // -1.0
        System.out.println("tan(PI/4): " + Math.tan(Math.PI / 4)); // 1.0

        // 10. Hyperbolic Functions
        System.out.println("sinh(1): " + Math.sinh(1)); // 1.175...
        System.out.println("cosh(1): " + Math.cosh(1)); // 1.543...
        System.out.println("tanh(1): " + Math.tanh(1)); // 0.761...

        // 11. Rounding Functions
        System.out.println("ceil(2.3): " + Math.ceil(2.3)); // 3.0
        System.out.println("floor(2.7): " + Math.floor(2.7)); // 2.0
        System.out.println("round(2.5): " + Math.round(2.5)); // 3

        // 12. Random Number (0.0 to 1.0)
        System.out.println("random(): " + Math.random());

        // 13. Signum Function
        System.out.println("signum(-5): " + Math.signum(-5)); // -1.0

        // 14. Hypotenuse Calculation
        System.out.println("hypot(3, 4): " + Math.hypot(3, 4)); // 5.0

        // 15. To Degrees and Radians
        System.out.println("toDegrees(PI): " + Math.toDegrees(Math.PI)); // 180.0
        System.out.println("toRadians(180): " + Math.toRadians(180)); // 3.141...

        // 16. IEEE Remainder
        System.out.println("IEEEremainder(10, 3): " + Math.IEEEremainder(-10, 3)); // 1.0

        // 17. Copy Sign
        System.out.println("copySign(5, -2): " + Math.copySign(5, -2)); // -5.0

        // 18. Next Up & Next Down
        System.out.println("nextUp(1.0): " + Math.nextUp(1.0)); // Slightly greater than 1
        System.out.println("nextDown(1.0): " + Math.nextDown(1.0)); // Slightly less than 1
    }
}
