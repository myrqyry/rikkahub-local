import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [index("routes/home.tsx"), route("c/:id", "routes/c.$id.tsx"), route("settings", "routes/settings.tsx")] satisfies RouteConfig;
