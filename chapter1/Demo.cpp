#include <iostream>
#include <algorithm>
using namespace std;

int main()
{
    int arr[] = {5, 6, 5, 2, 1, 4, 7, 7, 8, 9};

    int k = 3;

    sort(begin(arr),end(arr));
    
    for(int nums : arr){
       cout << nums << "  ";
    }

    for(int i =0 ; i < arr.size() ; i++)

    

    return 0;
}
