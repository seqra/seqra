package mcp

import (
	"context"
	"net/http"

	target "github.com/modelcontextprotocol/go-sdk/mcp"
)

// Server is a partial declaration for method receivers. All target fields stay unchanged.
type Server struct{}

func NewServer(_ *target.Implementation, options *target.ServerOptions) *target.Server {
	if options != nil {
		var ctx context.Context
		options.InitializedHandler(ctx, opentaintMCPIncoming[*target.InitializedRequest]())
		options.RootsListChangedHandler(ctx, opentaintMCPIncoming[*target.RootsListChangedRequest]())
		options.ProgressNotificationHandler(ctx, opentaintMCPIncoming[*target.ProgressNotificationServerRequest]())
		_, _ = options.CompletionHandler(ctx, opentaintMCPIncoming[*target.CompleteRequest]())
		_ = options.SubscribeHandler(ctx, opentaintMCPIncoming[*target.SubscribeRequest]())
		_ = options.UnsubscribeHandler(ctx, opentaintMCPIncoming[*target.UnsubscribeRequest]())
		_ = options.GetSessionID()
	}
	return nil
}

func (s *Server) AddPrompt(_ *target.Prompt, handler target.PromptHandler) {
	var ctx context.Context
	_, _ = handler(ctx, opentaintMCPIncoming[*target.GetPromptRequest]())
}

func (s *Server) AddTool(_ *target.Tool, handler target.ToolHandler) {
	var ctx context.Context
	_, _ = handler(ctx, opentaintMCPIncoming[*target.CallToolRequest]())
}

func AddTool[In, Out any](
	_ *target.Server,
	_ *target.Tool,
	handler target.ToolHandlerFor[In, Out],
) {
	var ctx context.Context
	_, _, _ = handler(ctx, opentaintMCPIncoming[*target.CallToolRequest](), opentaintMCPIncoming[In]())
}

func (s *Server) AddResource(_ *target.Resource, handler target.ResourceHandler) {
	var ctx context.Context
	_, _ = handler(ctx, opentaintMCPIncoming[*target.ReadResourceRequest]())
}

func (s *Server) AddResourceTemplate(_ *target.ResourceTemplate, handler target.ResourceHandler) {
	var ctx context.Context
	_, _ = handler(ctx, opentaintMCPIncoming[*target.ReadResourceRequest]())
}

func (s *Server) AddSendingMiddleware(middleware ...target.Middleware) {
	invokeMiddleware(middleware)
}

func (s *Server) AddReceivingMiddleware(middleware ...target.Middleware) {
	invokeMiddleware(middleware)
}

func NewStreamableHTTPHandler(
	getServer func(*http.Request) *target.Server,
	_ *target.StreamableHTTPOptions,
) *target.StreamableHTTPHandler {
	getServer(opentaintMCPIncoming[*http.Request]())
	return nil
}

func invokeMiddleware(middleware []target.Middleware) {
	var handler target.MethodHandler = emptyMethodHandler
	for _, wrap := range middleware {
		handler = wrap(handler)
	}
	var ctx context.Context
	_, _ = handler(ctx, opentaintMCPIncoming[string](), opentaintMCPIncoming[target.Request]())
}

func opentaintMCPIncoming[T any]() T {
	var value T
	return value
}

func emptyMethodHandler(context.Context, string, target.Request) (target.Result, error) {
	return nil, nil
}
