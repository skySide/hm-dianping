--- 1、获取商品的id以及保存到redis中的商品的key
local voucherId = ARGV[1]
--- lua脚本中通过..进行拼接字符串的
local voucherKey = "hm_dianping:seckill:voucher:stock:"..voucherId
--- 2、获取用户的id以及商品订单的key
local userId = ARGV[2]
local orderKey = "hm_dianping:seckill:order:voucher:"..voucherId
--- 3、获取商品的库存数量，判断是否充足
---这里需要利用tonumber，将返回值变成number类型，否则就会抛出异常attempt to compare boolean with number
if(tonumber (redis.call('get', voucherKey)) <= 0) then
    --- 库存不足
    return 1
end
--- 4、判断用户是否已经购买过这个商品了
if(tonumber(redis.call('sismember', orderKey, userId)) == 1) then
    --- 用户已经购买过了这个商品
    return 2
end
--- 5、更新库存，同时将这个用户添加到商品订单中，表示这个用户购买了这个商品
redis.call('incrby',voucherKey, -1)
redis.call('sadd', orderKey, userId)
return 0