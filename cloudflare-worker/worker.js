export default {
  async fetch(request, env) {
    try {
      const target = new URL(request.url);
      target.protocol = "http:";
      target.hostname = "127.0.0.1";
      target.port = "8080";

      return await env.PHONE_MCP.fetch(new Request(target, request));
    } catch (error) {
      console.error("VPC proxy request failed", error);
      return Response.json({ error: "phone_unavailable" }, { status: 502 });
    }
  },
};
