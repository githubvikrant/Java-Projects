```mermaid
flowchart TD
    subgraph Sender
        S1[Start App]
        S2[Create Group - WiFi Direct]
        S3[Wait for Peer Discovery]
        S4[Accept Connection - ServerSocket]
        S5[Handshake: Receive JOIN]
        S6[Ready to Send Files]
        S7[Send File Metadata & Data - Socket]
        S8[Show Progress]
        S9[File Transfer Complete]
    end
    subgraph Receiver
        R1[Start App]
        R2[Join Group - WiFi Direct]
        R3[Discover Peer]
        R4[Connect to Group Owner - Socket]
        R5[Handshake: Send JOIN]
        R6[Ready to Receive Files]
        R7[Receive File Metadata & Data - Socket]
        R8[Show Progress]
        R9[File Transfer Complete]
    end
    S1 --> S2 --> S3
    R1 --> R2 --> R3
    S3 -- Broadcast/Discovery --> R3
    R3 -- Select Peer --> R4
    R4 -- TCP Connect --> S4
    R5 -- JOIN Message --> S5
    S5 --> S6
    R4 --> R5 --> R6
    S6 -- User Selects File --> S7
    S7 -- Data --> R7
    S8 -- Progress Event --> S9
    R7 -- Progress Event --> R8
    S7 --> S8
    R7 --> R8
    S9 -- Notify Complete --> R9
    R8 --> R9