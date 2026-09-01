package net.erutobusiness.yeastnfeastjamfix.mixin;

import net.astralya.yeastnfeast.item.ModItems;
import net.astralya.yeastnfeast.item.custom.ConsumableItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Yeast 'n Feast の「ジャムを食べるとサーバから切断される」問題を止める。
 *
 * <p><b>機序</b>: 食べ終わりに {@code AbstractConsumableItem#finishUsingItem} が
 * {@code ConsumableItem#getReturnContainer} を呼び、そこが
 *
 * <pre>
 * stack.is(MOLASSES.get()) || stack.is(MAPLE_SYRUP.get()) || stack.is(GINGER_TEA.get())
 *     ? ガラス瓶 : 瓶(Jar)
 * </pre>
 *
 * <p>という判定をしている。ところが {@code ModItems.GINGER_TEA} は
 * <b>{@code if (ModList.isLoaded("farmersdelight"))} の中でしか登録されない</b>
 * （{@code ModItems} の static ブロックを逆アセンブルして確認）。
 * Farmer's Delight を入れていない構成では<b>欄が null のまま</b>残るのに、
 * 上の判定は<b>条件なしでその欄を触る</b>。
 *
 * <p><b>症状</b>: 糖蜜とメープルシロップは1つ目・2つ目の枝で返るので無事だが、
 * <b>それ以外の {@code ConsumableItem} は全部3つ目に到達する</b>＝<b>ジャム12種すべて</b>で
 * {@code NullPointerException} → {@code ReportedException: Ticking player} →
 * その人だけ {@code Internal server error} で切断される。
 *
 * <p><b>実測</b>（2026-09-01・当部の試験サーバ、台本 {@code yeastnfeast-jam-eat}）:
 * バニラの金リンゴと {@code yeastnfeast:molasses} は食べ終わり、
 * {@code yeastnfeast:lemon_jam} を食べた瞬間に切断された。
 * アップルジャムを1つも出していない台本でも同じ例外が出たので、
 * <b>特定のジャムに固有ではない</b>。
 *
 * <p><b>対処</b>: null になる枝だけを外した同じ判定に差し替える。
 * ⚠ <b>{@code GINGER_TEA} が非 null なら何もしない</b>ので、
 * Farmer's Delight を入れたときも、上流が登録を直したときも、
 * <b>このMODは黙って引っ込む</b>（入れっぱなしでも二重には効かない）。
 *
 * <p>⚠ {@code ChorusFruitJamItem} と {@code MapleSyrupItem} は
 * {@code getReturnContainer} を上書きしていないので、この1本で12種すべてに当たる。
 *
 * <p>{@code remap = false}: 当て先は Yeast 'n Feast 自身のメソッドで SRG マッピングの対象外。
 */
@Mixin(value = ConsumableItem.class, remap = false)
public class ConsumableItemMixin {

    @Inject(method = "getReturnContainer", at = @At("HEAD"), cancellable = true, remap = false)
    private void yeastnfeastJamFix$skipNullGingerTea(ItemStack stack,
                                                     CallbackInfoReturnable<ItemStack> cir) {
        // ⚠ 欄が埋まっているなら上流の判定がそのまま正しい。触らない。
        if (ModItems.GINGER_TEA != null) {
            return;
        }
        cir.setReturnValue(
                stack.is(ModItems.MOLASSES.get()) || stack.is(ModItems.MAPLE_SYRUP.get())
                        ? new ItemStack(Items.GLASS_BOTTLE)
                        : new ItemStack(ModItems.JAR.get()));
    }
}
