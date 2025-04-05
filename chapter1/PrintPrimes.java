// Write a Java program that takes an integer n as input and prints all prime numbers up to n. The program should use an efficient approach (not brute force).

import java.util.Scanner;

public class PrintPrimes {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.print("Enter the value of n: ");
        int n = input.nextInt();

        boolean[] isPrime = new boolean[n + 1];

        // Initially assume all numbers are prime
        for (int i = 2; i <= n; i++) {
            isPrime[i] = true;
        }

        // Sieve of Eratosthenes
        for (int p = 2; p * p <= n; p++) {
            if (isPrime[p]) {
                for (int multiple = p * p; multiple <= n; multiple += p) {
                    isPrime[multiple] = false;
                }
            }
        }

        // Print all prime numbers
        System.out.println("Prime numbers up to " + n + " are:");
        for (int i = 2; i <= n; i++) {
            if (isPrime[i]) {
                System.out.print(i + " ");
            }
        }

        input.close();
    }
}




// Efficient Way: Sieve of Eratosthenes
// Here’s how it works:

// Create a boolean array isPrime[] and set all values to true initially.

// Mark isPrime[0] and isPrime[1] as false (0 and 1 are not primes).

// Start with p = 2, and mark all multiples of p (like 2*2, 2*3, 2*4, etc.) as false.

// Move to the next number p = 3, and again mark all multiples of p as false.

// Repeat this until p * p <= n.

// In the end, all isPrime[i] that are still true are prime numbers.
