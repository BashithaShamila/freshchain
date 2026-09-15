import { AuthProvider } from "@asgardeo/auth-react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { APP_BASE_URL, ASGARDEO_BASE_URL, ASGARDEO_CLIENT_ID } from "./config";
import "./styles.css";

const authConfig = {
  signInRedirectURL: APP_BASE_URL,
  signOutRedirectURL: APP_BASE_URL,
  clientID: ASGARDEO_CLIENT_ID,
  baseUrl: ASGARDEO_BASE_URL,
  // `roles` and `groups` are what carry authorisation; without them the token
  // arrives with nothing this app or the services can act on.
  scope: ["openid", "profile", "roles", "groups"],
};

createRoot(document.getElementById("root") as HTMLElement).render(
  <StrictMode>
    <AuthProvider config={authConfig}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>,
);
