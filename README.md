# Yeast 'n Feast Jam Fix

A one-fix compatibility mod for Minecraft 1.20.1 (Forge). It stops
[Yeast 'n Feast](https://modrinth.com/mod/yeast-n-feast) from disconnecting a player
who finishes eating a jam, and does nothing else.

- **Minecraft**: 1.20.1 / **Loader**: Forge 47+ / **Side**: both
- **License**: MIT

## 何が起きるか

ジャムを食べ終わった瞬間に、**サーバ側で例外が飛んでその人だけ切断される**。

```
java.lang.NullPointerException: Cannot invoke "RegistryObject.get()"
because "net.astralya.yeastnfeast.item.ModItems.GINGER_TEA" is null
  at ConsumableItem.getReturnContainer(ConsumableItem.java:28)
  at AbstractConsumableItem.m_5922_(AbstractConsumableItem.java:37)   ← 食べ終わり
  → net.minecraft.ReportedException: Ticking player
  → <player> lost connection: Internal server error
```

## なぜ起きるか

食べ終わりに「空になった器を何で返すか」を決める判定がこうなっている:

```java
stack.is(MOLASSES.get()) || stack.is(MAPLE_SYRUP.get()) || stack.is(GINGER_TEA.get())
    ? new ItemStack(Items.GLASS_BOTTLE) : new ItemStack(ModItems.JAR.get());
```

ところが `ModItems.GINGER_TEA` は **`if (ModList.isLoaded("farmersdelight"))` の中でしか
登録されない**（`ModItems` の static ブロックを逆アセンブルして確認）。
Farmer's Delight を入れていない構成では**欄が null のまま**残るのに、
上の判定は**条件なしでその欄を触る**。

⚠ **糖蜜とメープルシロップは1つ目・2つ目の枝で返る**ので無事。
⚠⚠ **それ以外の `ConsumableItem` は全部3つ目に到達する＝ジャム12種すべてが落ちる。**

| 落ちるもの（12件） |
| - |
| `apple_jam` `chillberries_jam` `chorus_fruit_jam` `elderberries_jam` `glow_berries_jam` `golden_apple_jam` `hawthorn_berries_jam` `lemon_jam` `melon_jam` `rose_hips_jam` `strawberries_jam` `sweet_berries_jam` |

`ChorusFruitJamItem` と `MapleSyrupItem` は `getReturnContainer` を上書きしていないので、
mixin 1本で12種すべてに当たる。

## 実測（2026-09-01）

台本 `yeastnfeast-jam-eat`（当部の自動確認）を専用サーバ＋クライアントで回した結果:

| 食べたもの | 結果 |
| - | - |
| `minecraft:golden_apple`（バニラ・陽性対照） | 食べ終わった |
| `yeastnfeast:molasses`（同じ `ConsumableItem`・陰性対照） | 食べ終わった |
| `yeastnfeast:lemon_jam` | ⚠ **食べた瞬間に切断された** |

⚠ **アップルジャムを1つも出していない台本で再現した**ので、特定のジャムに固有ではない。
切断時に保存されたプレイヤーデータでも裏が取れている（金リンゴと糖蜜は
Spice of Life の `foodList` に入っており＝食べ終わっている、レモンジャムは
`Inventory` に残っている＝食べている最中に落ちた）。

## 何をするか

null になる枝だけを外した同じ判定に差し替える。

⚠ **`GINGER_TEA` が非 null なら何もしない。**
Farmer's Delight を入れたときも、上流が登録を直したときも、このMODは黙って引っ込む
（入れっぱなしでも二重には効かない）。

## 上流

`forge` 枝も `neoforge-1.21.1` 枝も同じ行のまま（2026-09-01 時点）。
`fabric` 枝だけ **ginger tea の枝がそもそも無い**ので、上流の意図は「その枝は要らない」側に見える。
