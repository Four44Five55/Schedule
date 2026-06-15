import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App";
import { EnumProvider } from "./context/EnumContext";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <EnumProvider>
            <App />
        </EnumProvider>
    </StrictMode>
);
