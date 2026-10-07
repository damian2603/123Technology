package com.newmaa.othtech.machine.machineclass;

import static gregtech.api.enums.GTValues.V;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTUtility.validMTEList;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;

import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.enums.TieredVariant;
import gregtech.api.gui.modularui.CircularGaugeDrawable;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchVoidBus;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.objects.overclockdescriber.SteamOverclockDescriber;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

public abstract class OTHSteamMultiBase<T extends OTHSteamMultiBase<T>> extends GTPPMultiBlockBase<T>
    implements IOverclockDescriptionProvider {

    private final OverclockDescriber overclockDescriber;

    public ArrayList<MTEHatchSteamBusInput> mSteamInputs = new ArrayList<>();
    public ArrayList<MTEHatchOutputBus> mSteamOutputs = new ArrayList<>();
    public ArrayList<MTEHatchCustomFluidBase> mSteamInputFluids = new ArrayList<>();

    public OTHSteamMultiBase(String aName) {
        super(aName);
        this.overclockDescriber = createOverclockDescriber();
    }

    public OTHSteamMultiBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        this.overclockDescriber = createOverclockDescriber();
    }

    @Override
    public ITexture[] getTexture(final IGregTechTileEntity aBaseMetaTileEntity, final ForgeDirection side,
        final ForgeDirection facing, final int aColorIndex, final boolean aActive, final boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureIndex()),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureIndex()) };
    }

    protected abstract ITexture getFrontOverlay();

    protected abstract ITexture getFrontOverlayActive();

    public abstract int getTierRecipes();

    private int getCasingTextureIndex() {
        return 10;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        logic.setAvailableVoltage(V[getTierRecipes()]);
        logic.setAvailableAmperage(getMaxParallelRecipes());
        logic.setAmperageOC(false);
        logic.setMaxTierSkips(0);
    }

    public ArrayList<FluidStack> getAllSteamStacks() {
        ArrayList<FluidStack> aFluids = new ArrayList<>();
        FluidStack aSteam = Materials.Steam.getGas(1);
        for (FluidStack aFluid : this.getStoredFluids()) {
            if (aFluid.isFluidEqual(aSteam)) {
                aFluids.add(aFluid);
            }
        }
        return aFluids;
    }

    public int getTotalSteamStored() {
        int aSteam = 0;
        for (FluidStack aFluid : getAllSteamStacks()) {
            aSteam += aFluid.amount;
        }
        return aSteam;
    }

    public int getTotalSteamCapacity() {
        int aSteam = 0;
        for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
            aSteam += tHatch.getRealCapacity();
        }
        return aSteam;
    }

    public boolean tryConsumeSteam(int aAmount) {
        if (getTotalSteamStored() <= 0) {
            return false;
        } else {
            return this.depleteInput(Materials.Steam.getGas(aAmount));
        }
    }

    @Override
    public int getMaxEfficiency(ItemStack arg0) {
        return 0;
    }

    @Override
    public void onPostTick(final IGregTechTileEntity aBaseMetaTileEntity, final long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) {
            if (this.mUpdate == 1 || this.mStartUpCheck == 1) {
                this.mSteamInputs.clear();
                this.mSteamOutputs.clear();
                this.mInputHatches.clear();
                this.mSteamInputFluids.clear();
                this.mOutputHatches.clear();
            }
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (lEUt < 0) {
            long aSteamVal = ((-lEUt * 10000) / Math.max(1000, mEfficiency));
            if (!tryConsumeSteam((int) aSteamVal)) {
                stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                return false;
            }
        }
        return true;
    }

    public <E> boolean addToMachineListInternal(ArrayList<E> aList, final E aTileEntity, final int aBaseCasingIndex) {
        if (aTileEntity == null) return false;

        if (aTileEntity instanceof MTEHatch mteHatch) {
            mteHatch.updateTexture(aBaseCasingIndex);
            mteHatch.updateCraftingIcon(this.getMachineCraftingIcon());
        }

        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap != null) {
            if (aTileEntity instanceof MTEHatchInput hatch) hatch.mRecipeMap = recipeMap;
            if (aTileEntity instanceof MTEHatchInputBus hatch) hatch.mRecipeMap = recipeMap;
        }

        if (aList.contains(aTileEntity)) return false;
        if (!aList.add(aTileEntity)) return false;
        if (aTileEntity instanceof IMetaTileEntity mte) addIfSmartInput(mte);
        return true;
    }

    @Override
    public boolean addToMachineList(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;

        if (addSteamInputFluidHatch(aTileEntity, aBaseCasingIndex)) return true;
        if (addSteamBusInput(aTileEntity, aBaseCasingIndex)) return true;
        if (addSteamBusOutput(aTileEntity, aBaseCasingIndex)) return true;

        return super.addToMachineList(aTileEntity, aBaseCasingIndex)
            || addExoticEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
    }

    public boolean addSteamBusInput(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;

        if (aMetaTileEntity instanceof MTEHatchSteamBusInput steamBus) {
            RecipeMap<?> recipeMap = getRecipeMap();
            if (recipeMap != null) this.resetRecipeMapForHatch(steamBus, recipeMap);
            return addToMachineListInternal(mSteamInputs, steamBus, aBaseCasingIndex);
        }
        return false;
    }

    public boolean addSteamBusOutput(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;

        if (aMetaTileEntity instanceof MTEHatchSteamBusOutput || aMetaTileEntity instanceof MTEHatchVoidBus) {
            return addToMachineListInternal(mSteamOutputs, (MTEHatchOutputBus) aMetaTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    public boolean addSteamInputFluidHatch(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;

        if (aMetaTileEntity instanceof MTEHatchCustomFluidBase fluidHatch
            && Materials.Steam.mGas.equals(fluidHatch.mLockedFluid)
            && mSteamInputFluids.isEmpty()) {
            return addToMachineListInternal(mSteamInputFluids, fluidHatch, aBaseCasingIndex);
        }
        return false;
    }

    @Override
    public boolean depleteInput(FluidStack aLiquid) {
        if (aLiquid == null) return false;
        for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
            FluidStack tLiquid = tHatch.getFluid();
            if (tLiquid != null && tLiquid.isFluidEqual(aLiquid)) {
                tLiquid = tHatch.drain(aLiquid.amount, false);
                if (tLiquid != null && tLiquid.amount >= aLiquid.amount) {
                    tLiquid = tHatch.drain(aLiquid.amount, true);
                    return tLiquid != null && tLiquid.amount >= aLiquid.amount;
                }
            }
        }
        return false;
    }

    @Override
    public boolean depleteInput(ItemStack aStack) {
        if (GTUtility.isStackInvalid(aStack)) return false;
        FluidStack aLiquid = GTUtility.getFluidForFilledItem(aStack, true);
        if (aLiquid != null) return depleteInput(aLiquid);

        for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
            final IGregTechTileEntity baseMetaTileEntity = tHatch.getBaseMetaTileEntity();
            ItemStack stackInSlot = baseMetaTileEntity.getStackInSlot(0);
            if (GTUtility.areStacksEqual(aStack, stackInSlot) && stackInSlot.stackSize >= aStack.stackSize) {
                baseMetaTileEntity.decrStackSize(0, aStack.stackSize);
                return true;
            }
        }

        for (MTEHatchSteamBusInput tHatch : validMTEList(mSteamInputs)) {
            final IGregTechTileEntity baseMetaTileEntity = tHatch.getBaseMetaTileEntity();
            for (int i = baseMetaTileEntity.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack stackInSlot = baseMetaTileEntity.getStackInSlot(i);
                if (GTUtility.areStacksEqual(aStack, stackInSlot) && stackInSlot.stackSize >= aStack.stackSize) {
                    baseMetaTileEntity.decrStackSize(i, aStack.stackSize);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ArrayList<FluidStack> getStoredFluidsForColor(Optional<Byte> color) {
        ArrayList<FluidStack> rList = new ArrayList<>();
        for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
            byte hatchColor = tHatch.getBaseMetaTileEntity()
                .getColorization();
            if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
            if (tHatch.getFillableStack() != null) rList.add(tHatch.getFillableStack());
        }
        for (MTEHatchInput hatch : this.mInputHatches) {
            if (hatch.getFillableStack() == null) continue;
            byte hatchColor = hatch.getBaseMetaTileEntity()
                .getColorization();
            if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
            rList.add(hatch.getFillableStack());
        }
        return rList;
    }

    @Override
    public ArrayList<ItemStack> getStoredInputsForColor(Optional<Byte> color) {
        ArrayList<ItemStack> rList = new ArrayList<>();

        for (MTEHatchInputBus tHatch : validMTEList(mInputBusses)) {
            byte hatchColor = tHatch.getBaseMetaTileEntity()
                .getColorization();
            if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
            for (int i = tHatch.getBaseMetaTileEntity()
                .getSizeInventory() - 1; i >= 0; i--) {
                ItemStack stack = tHatch.getBaseMetaTileEntity()
                    .getStackInSlot(i);
                if (stack != null) rList.add(stack);
            }
        }

        RecipeMap<?> recipeMap = getRecipeMap();
        for (MTEHatchSteamBusInput tHatch : validMTEList(mSteamInputs)) {
            byte hatchColor = tHatch.getBaseMetaTileEntity()
                .getColorization();
            if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
            if (recipeMap != null) tHatch.mRecipeMap = recipeMap;
            for (int i = tHatch.getBaseMetaTileEntity()
                .getSizeInventory() - 1; i >= 0; i--) {
                ItemStack stack = tHatch.getBaseMetaTileEntity()
                    .getStackInSlot(i);
                if (stack != null) rList.add(stack);
            }
        }
        return rList;
    }

    @Override
    public List<IOutputBus> getOutputBusses() {
        List<IOutputBus> output = new ArrayList<>(super.getOutputBusses());
        for (MTEHatchOutputBus outputBus : mSteamOutputs) {
            if (outputBus.isValid() && !output.contains(outputBus)) output.add(outputBus);
        }
        return output;
    }

    @Override
    public void updateSlots() {
        for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) tHatch.updateSlots();
        for (MTEHatchSteamBusInput tHatch : validMTEList(mSteamInputs)) tHatch.updateSlots();
        for (MTEHatchInputBus tHatch : validMTEList(mInputBusses)) tHatch.updateSlots();
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        mInputHatches.clear();
        mSteamInputFluids.clear();
        mSteamInputs.clear();
        mSteamOutputs.clear();
        mOutputHatches.clear();
    }

    public static boolean isColorAbsent(short hatchColors, byte color) {
        return (hatchColors & (1 << color)) == 0;
    }

    public short getHatchColors() {
        short hatchColors = 0;

        for (var bus : mInputBusses) hatchColors |= (short) (1 << bus.getColor());
        for (var hatch : mInputHatches) hatchColors |= (short) (1 << hatch.getColor());
        for (var bus : mSteamInputs) hatchColors |= (short) (1 << bus.getColor());
        for (var hatch : mSteamInputFluids) hatchColors |= (short) (1 << hatch.getColor());

        return hatchColors;
    }

    @Override
    @Nonnull
    public CheckRecipeResult doCheckRecipe() {
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;

        for (IDualInputHatch dualInputHatch : mDualInputHatches) {
            ItemStack[] sharedItems = dualInputHatch.getSharedItems();
            for (var it = dualInputHatch.inventories(); it.hasNext();) {
                IDualInputInventory slot = it.next();

                if (!slot.isEmpty()) {
                    if (slot instanceof IDualInputInventoryWithPattern withPattern) {
                        if (!processingLogic.tryCachePossibleRecipesFromPattern(withPattern)) continue;
                    }

                    processingLogic.setInputItems(ArrayUtils.addAll(sharedItems, slot.getItemInputs()));
                    processingLogic.setInputFluids(slot.getFluidInputs());

                    CheckRecipeResult foundResult = processingLogic.process();
                    if (foundResult.wasSuccessful()) return foundResult;
                    if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
                }
            }
        }

        result = checkRecipeForCustomHatches(result);
        if (result.wasSuccessful()) return result;

        short hatchColors = getHatchColors();
        boolean doColorChecking = hatchColors != 0;
        if (!doColorChecking) hatchColors = 0b1;

        for (byte color = 0; color < (doColorChecking ? 16 : 1); color++) {
            if (isColorAbsent(hatchColors, color)) continue;
            processingLogic.setInputFluids(getStoredFluidsForColor(Optional.of(color)));
            if (isInputSeparationEnabled()) {
                if (mInputBusses.isEmpty() && mSteamInputs.isEmpty()) {
                    CheckRecipeResult foundResult = processingLogic.process();
                    if (foundResult.wasSuccessful()) return foundResult;
                    if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
                } else {
                    for (MTEHatchInputBus bus : mInputBusses) {
                        if (bus instanceof MTEHatchCraftingInputME) continue;
                        byte busColor = bus.getColor();
                        if (busColor != -1 && busColor != color) continue;
                        List<ItemStack> inputItems = new ArrayList<>();
                        for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
                            ItemStack stored = bus.getStackInSlot(i);
                            if (stored != null) inputItems.add(stored);
                        }
                        if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
                            inputItems.add(getControllerSlot());
                        }
                        processingLogic.setInputItems(inputItems);
                        CheckRecipeResult foundResult = processingLogic.process();
                        if (foundResult.wasSuccessful()) return foundResult;
                        if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
                    }
                    for (MTEHatchSteamBusInput bus : mSteamInputs) {
                        byte busColor = bus.getColor();
                        if (busColor != -1 && busColor != color) continue;
                        List<ItemStack> inputItems = new ArrayList<>();
                        for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
                            ItemStack stored = bus.getStackInSlot(i);
                            if (stored != null) inputItems.add(stored);
                        }
                        if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
                            inputItems.add(getControllerSlot());
                        }
                        processingLogic.setInputItems(inputItems);
                        CheckRecipeResult foundResult = processingLogic.process();
                        if (foundResult.wasSuccessful()) return foundResult;
                        if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
                    }
                }
            } else {
                List<ItemStack> inputItems = getStoredInputsForColor(Optional.of(color));
                if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
                    inputItems.add(getControllerSlot());
                }
                processingLogic.setInputItems(inputItems);
                CheckRecipeResult foundResult = processingLogic.process();
                if (foundResult.wasSuccessful()) return foundResult;
                if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
            }
        }
        return result;
    }

    @Override
    public boolean resetRecipeMapForAllInputHatches(RecipeMap<?> aMap) {
        boolean ret = super.resetRecipeMapForAllInputHatches(aMap);
        for (MTEHatchSteamBusInput hatch : mSteamInputs) {
            if (resetRecipeMapForHatch(hatch, aMap)) ret = true;
        }
        for (MTEHatchInput g : this.mInputHatches) {
            if (resetRecipeMapForHatch(g, aMap)) ret = true;
        }
        return ret;
    }

    private int uiSteamStored = 0;
    private int uiSteamCapacity = 0;

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);
        builder.widget(new FakeSyncWidget.IntegerSyncer(this::getTotalSteamCapacity, val -> uiSteamCapacity = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(this::getTotalSteamStored, val -> uiSteamStored = val));

        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.STEAM_GAUGE_BG_STEEL)
                .dynamicTooltip(
                    () -> Collections.singletonList(
                        translateToLocalFormatted(
                            MTEBasicMachine.STEAM_AMOUNT_LANGKEY,
                            numberFormat.format(uiSteamStored),
                            numberFormat.format(uiSteamCapacity))))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setUpdateTooltipEveryTick(true)
                .setSize(48, 42)
                .setPos(-48, -8));

        builder.widget(
            new DrawableWidget().setDrawable(
                new CircularGaugeDrawable(() -> uiSteamCapacity <= 0 ? 0F : (float) uiSteamStored / uiSteamCapacity))
                .setPos(-48 + 21, -8 + 21)
                .setSize(18, 4));
    }

    protected static <T extends OTHSteamMultiBase<T>> HatchElementBuilder<T> buildSteamInput(Class<T> typeToken) {
        return buildHatchAdder(typeToken).adder(OTHSteamMultiBase::addSteamInputFluidHatch)
            .hatchIds(31040)
            .shouldReject(t -> !t.mSteamInputFluids.isEmpty());
    }

    protected static OverclockDescriber createOverclockDescriber() {
        return new SteamOverclockDescriber(TieredVariant.BRONZE, 1, 2);
    }

    @Override
    public @Nullable OverclockDescriber getOverclockDescriber() {
        return overclockDescriber;
    }

    protected enum SteamHatchElement implements IHatchElement<OTHSteamMultiBase<?>> {

        InputBus_Steam {

            @Override
            public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
                return Collections.singletonList(MTEHatchSteamBusInput.class);
            }

            @Override
            public long count(OTHSteamMultiBase<?> t) {
                return t.mSteamInputs.size();
            }

            @Override
            public IGTHatchAdder<? super OTHSteamMultiBase<?>> adder() {
                return OTHSteamMultiBase::addSteamBusInput;
            }
        },
        OutputBus_Steam {

            @Override
            public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
                return Collections.singletonList(MTEHatchSteamBusOutput.class);
            }

            @Override
            public long count(OTHSteamMultiBase<?> t) {
                return t.mSteamOutputs.size();
            }

            @Override
            public IGTHatchAdder<? super OTHSteamMultiBase<?>> adder() {
                return OTHSteamMultiBase::addSteamBusOutput;
            }
        };
    }

    @Override
    public boolean getDefaultInputSeparationMode() {
        return true;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public void checkHatch(List<StructureError> errors) {
        super.checkHatch(errors);
    }
}
