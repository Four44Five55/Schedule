import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App";
import { EnumProvider } from "./context/EnumContext";
import { ToastProvider } from "./context/ToastContext";
import { UnhandledFailureWatch } from "./context/UnhandledFailureWatch";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        {/* Тосты — самый внешний слой: об отказе загрузки должны уметь сообщить и провайдеры
            под ним (справочники, периоды), а не только экраны. */}
        <ToastProvider>
            {/* Сетка под отказы, которые никто не поймал: показывает то, что иначе осталось бы
                только в консоли. Внутри провайдера — ей нужен тост. */}
            <UnhandledFailureWatch />
            <EnumProvider>
                <App />
            </EnumProvider>
        </ToastProvider>
    </StrictMode>
);
