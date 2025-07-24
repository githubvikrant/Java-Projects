     ```mermaid
     
3. **You must open the Markdown Preview:**
   - Open your `.md` file.
   - Press `Ctrl+Shift+V` (or right-click and select “Open Preview”).
   - If using Markdown Preview Enhanced, use the command palette (`Ctrl+Shift+P`) and search for “Markdown Preview Enhanced: Open Preview”.

4. **No spaces before the backticks or code block.**
   - The code block must start at the beginning of the line.

---

## **Minimal Working Example**

Copy and paste this into a new `.md` file and preview it:

<pre>
```mermaid
flowchart TD
    A[Main.java]
    B[Main (extends Application)]
    C[MainWindow]
    D[WiFiDirectService]
    E[FileTransferService]
    F[JavaFX Application Thread]
    G[JavaFX UI]
    H[Peer Discovery]
    I[Connection Management]
    J[File Transfer]

    A --> B
    B -->|start()| F
    F -->|launches| C
    C -->|creates| D
    C -->|creates| E
    C --> G
    D --> H
    D --> I
    E --> J
    G -->|user actions| C
    C -->|event handlers| E
    C -->|event handlers| D
```
</pre>

---

## **If It Still Doesn’t Work:**
- Try the same code in [https://mermaid.live/](https://mermaid.live/) to confirm it’s valid.
- If it works online but not in VS Code, the issue is with your VS Code setup (extension missing or not enabled).

---

**If you want, tell me which extension you have installed, and I’ll give you step-by-step instructions for that one!**s