# Binary Trading Protocol

`finex-protocol` defines a compact, length-prefixed binary wire format used for the hot
path (gateway → matching engine or inter-service messaging). It is **not** the REST API
protocol; that is plain JSON over HTTP.

## Frame layout

Every message is a single frame:

```text
| 4 bytes payload length (big-endian int) | 1 byte message type | payload ... |
```

The payload length is the number of bytes *after* the length prefix, i.e. it includes the
1-byte type plus the payload body. The reader must receive exactly `length` bytes before
decoding. Partial frames are not supported in this baseline.

## Primitive encodings

- **Strings** are encoded as a 4-byte length followed by UTF-8 bytes.
  A length of `-1` represents `null` and is only used for optional prices.
- **BigDecimal** values are encoded as their plain string form (e.g. `"50000.00"`).
- **Enums** are encoded as a single byte ordinal. Enum declaration order is therefore part
  of the wire contract.

## Message types

| Type byte | Message | Direction | Notes |
|-----------|---------|-----------|-------|
| `1` | `NewOrder` | inbound | Includes account id, client order id, symbol, side, type, price, quantity |
| `2` | `CancelOrder` | inbound | Account id + order id |
| `3` | `ModifyOrder` | inbound | Account id + order id + new price/quantity |
| `4` | `OrderAck` | outbound | Order accepted and resting/filled |
| `5` | `OrderRejected` | outbound | Order id + rejection reason |
| `6` | `ExecutionReport` | outbound | Fill/update for an order |

`BinaryCodec.encode(ProtocolMessage)` and `BinaryCodec.decode(ByteBuffer)` implement the
above. `ProtocolMessage` is a sealed record hierarchy; adding a new message type requires
updating `BinaryCodec` and any switch-based consumers.

## Usage

```java
ProtocolMessage.NewOrder msg = new ProtocolMessage.NewOrder(
        100L, "cid-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
        new BigDecimal("50000"), new BigDecimal("1"));
ByteBuffer frame = BinaryCodec.encode(msg);
ProtocolMessage decoded = BinaryCodec.decode(frame);
```

## Evolution notes

This is a baseline codec. Production evolution would add a schema/version field, optional
fields, and backward-compatible decoding rules. For the current scope, message type +
fixed field order is sufficient.
