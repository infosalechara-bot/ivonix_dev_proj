# PULSE Request ID v1

Header `X-Request-ID`. Valid values are `req_` plus exactly 21 lowercase alphanumeric characters. Invalid or absent inbound values are replaced at the trusted ingress boundary and propagated downstream.
