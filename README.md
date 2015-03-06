# nu-pool
Basic implementation of a proof-of-liquidity pool concept to in order to provide distributed NuBit liquidity.

**Participants**

1. Register at the pool operator (bitmessage/website/whatever) by providing your API key and an NBT payout address
2. Run the NuBot (with multiple-custodians=true to sync the walls)
3. Create requests to show your orders on the exchange, sign them with the API secret, and send them to the pool operator

**Pool Operator**

1. Get a custodial grant
2. Run a server that receives requests from users
3. Validate incoming orders by calling the exchange API
4. Send a payout to the NBT address given in the registration of the corresponding API key
5. Submit sum of orders as liquidity
