package tech.qvanphong;

import io.neow3j.devpack.*;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.annotations.*;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import java.util.ArrayList;

@DisplayName("BIT NFT")
@ManifestExtra(key = "Author", value = "Phong")
@ManifestExtra(key = "Version", value = "Testnet")
@ManifestExtra(key = "Contract", value = "I hate everybody so please don't find me.")
@SupportedStandards("NEP-11")
@Permission(contract = "*", methods = {"onNEP17Payment", "onNEP11Payment"})
@Permission(contract = "fffdc93764dbaddd97c48f252a53ea4643faa3fd") // ContractManagement
public class BitNFTContract {
    static final Hash160 contractOwner = StringLiteralHelper.addressToScriptHash("NRYzFhRPrhdJ269yaGzEDHYr964SC1yLx2");
    static final int initialSupply = 20;
    static final int mintGASPrice = 2;

    static final byte[] contractPrefix = Helper.toByteArray("contract_");
    static final byte[] ownerPrefix = Helper.toByteArray("owner_");
    static final byte[] balancePrefix = Helper.toByteArray("balance_");
    static final byte[] registryPrefix = Helper.toByteArray("registry_");
    static final byte[] tokensOfKey = Helper.toByteArray("token_of_");
    static final byte[] propName = Helper.toByteArray("name_");
    static final byte[] propDescription = Helper.toByteArray("description_");
    static final byte[] propImage = Helper.toByteArray("image_");
    static final byte[] propURI = Helper.toByteArray("uri_");

    static final byte[] initialSupplyKey = Helper.toByteArray("initialSupply");
    static final byte[] totalSupplyKey = Helper.toByteArray("totalSupply");
    static final byte[] contractMinted = Helper.toByteArray("minted");
    static final byte[] contractBurned = Helper.toByteArray("burned");


    static final StorageContext ctx = Storage.getStorageContext();
    /*
     * Save contract properties, like initial supply, total supply, ...
     * Key: property name - String
     * Value: value - Any
     * */
    static final StorageMap contractMap = ctx.createMap(contractPrefix);

    /*
     * Token's owner storage map, represent who is owner of token id
     * Key: Token id ByteArray
     * Value: Address ByteArray
     * */
    static final StorageMap ownerMap = ctx.createMap(ownerPrefix);
    /*
     * Save registered token ids (minted tokens), use for mint and burn
     * Key: Token id ByteArray
     * Value: Token id ByteArray
     * */
    static final StorageMap registryMap = ctx.createMap(registryPrefix);
    /*
     * Save amount owned NFT of every address
     * Key: Address ByteArray
     * Value: Integer amount
     * */
    static final StorageMap balanceMap = ctx.createMap(balancePrefix);
    /*
     * Properties of token: Name
     * */
    static final StorageMap propNameMap = ctx.createMap(propName);
    /*
     * Properties of token: Uri
     * */
    static final StorageMap propURIMap = ctx.createMap(propURI);
    /*
     * Properties of token: Image
     * */
    static final StorageMap propImageMap = ctx.createMap(propImage);
    /*
     * Properties of token: Description
     * */
    static final StorageMap propDescriptionMap = ctx.createMap(propDescription);

    /*
     * Transfer Event
     * */
    @DisplayName("Transfer")
    static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    /*
     * Mint Event
     * */
    @DisplayName("Mint")
    private static Event3Args<Hash160, ByteString, Map<String, String>> onMint;

    // Methods for contract update/destroy/deploy
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            contractMap.put(initialSupplyKey, initialSupply);
            contractMap.put(totalSupplyKey, 0);
            contractMap.put(contractBurned, 0);
            contractMap.put(contractMinted, 0);
        }
    }

    public static void update(ByteString script, String manifest) throws Exception {
        if (!Runtime.checkWitness(contractOwner)) throw new Exception("You are not my master, get out!");
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("The new contract script and manifest must not be empty.");
        }
        ContractManagement.update(script, manifest);
    }

    public static boolean destroy() {
        if (!Runtime.checkWitness(contractOwner)) {
            return false;
        }
        ContractManagement.destroy();
        return true;
    }

    @OnNEP17Payment
    public static void onNEP17Payment(Hash160 sender, int amount, Object data) throws Exception {
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();
        Hash160 hash = GasToken.getHash();
        if (callingScriptHash.equals(hash)) {
            // gas decimals
            if (amount != (100000000 * mintGASPrice)) throw new Exception("GAS amount must be" + mintGASPrice);
            mint(sender);
        }
    }

    /*
     * Begin mint new token, only accessible from onNEP17Payment.
     * */
    public static void mint(Hash160 from) throws Exception {
        int mintedAmount = contractMap.getInteger(contractBurned);

        if (mintedAmount >= initialSupply) throw new Exception("No more available NFTs to mint");

        int currentTotalSupply = totalSupply();
        int updatedSupply = currentTotalSupply + 1;
        String updatedSupplyString = StdLib.itoa(updatedSupply, 10);
        ByteString tokenId = new ByteString(Helper.toByteArray(updatedSupply));

        String tokenName = "BIT #" + updatedSupplyString;
        String tokenDescription = "BIT NFT #" + updatedSupplyString;
        String tokenImage = updatedSupplyString + ".png";
        String tokenURI = "Update later";

        Map<String, String> properties = new Map<>();
        properties.put("name", tokenName);
        properties.put("description", tokenDescription);
        properties.put("image", tokenImage);
        properties.put("URI", tokenURI);


        propNameMap.put(tokenId, tokenName);
        propDescriptionMap.put(tokenId, tokenDescription);
        propImageMap.put(tokenId, tokenImage);
        propURIMap.put(tokenId, tokenURI);

        registryMap.put(tokenId, tokenId);
        ownerMap.put(tokenId, from.toByteArray());
        // Save owner's owned token list
        ctx.createMap(createTokensOfPrefix(from)).put(tokenId, tokenId);

        increaseMintedByOne();
        incrementBalanceByOne(from);
        onMint.fire(from, tokenId, properties);
        onTransfer.fire(null, from, 1, tokenId);
    }

    @Safe
    public static String symbol() {
        return "BITNFT";
    }

    @Safe
    public static int decimals() {
        return 0;
    }

    @Safe
    public static int totalSupply() {
        return contractMap.getInteger(contractMinted) - contractMap.getInteger(contractBurned);
    }

    @Safe
    public static int balanceOf(Hash160 account) {
        return getBalance(account);
    }

    @Safe
    public static Hash160 ownerOf(ByteString tokenId) {
        byte[] ownerBytes = ownerMap.getByteArray(tokenId.toByteArray());
        return ownerBytes == null || ownerBytes.length == 0 ? null : new Hash160(ownerBytes);
    }

    @Safe
    public static Map<String, String> properties(ByteString tokenId) throws Exception {
        Map<String, String> property = new Map<>();

        ByteString propertyName = propNameMap.get(tokenId);
        if (propertyName == null) {
            throw new Exception("Token not exist");
        }

        property.put("name", propertyName.toString());

        ByteString propertyUri = propURIMap.get(tokenId);
        if (propertyUri != null) property.put("URI", propertyUri.toString());

        ByteString propertyDescription = propDescriptionMap.get(tokenId);
        if (propertyDescription != null) property.put("description", propertyDescription.toString());

        ByteString propertyImage = propImageMap.get(tokenId);
        if (propertyImage != null) property.put("image", propertyImage.toString());

        return property;
    }

    @Safe
    public static Iterator<ByteString> tokens() {
        return (Iterator<ByteString>) Storage.find(
                ctx.asReadOnly(),
                registryPrefix,
                FindOptions.ValuesOnly);
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(
                ctx.asReadOnly(),
                createTokensOfPrefix(owner),
                FindOptions.ValuesOnly);
    }

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        if (owner == null) {
            throw new Exception("This token id does not exist.");
        }
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("No authorization.");
        }
        ownerMap.put(tokenId, to.toByteArray());
        ctx.createMap(createTokensOfPrefix(owner)).delete(tokenId);
        ctx.createMap(createTokensOfPrefix(to)).put(tokenId, 1);

        decrementBalanceByOne(owner);
        incrementBalanceByOne(to);

        onTransfer.fire(owner, to, 1, tokenId);

        if (ContractManagement.getContract(to) != null) {
            Contract.call(to, "onNEP11Payment", CallFlags.All,
                    new Object[]{owner, 1, tokenId, data});
        }
        return true;
    }

    public static boolean burn(ByteString tokenId) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        if (owner == null) {
            throw new Exception("This token id does not exist.");
        }
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("No authorization.");
        }
        registryMap.delete(tokenId);
        propImageMap.delete(tokenId);
        propDescriptionMap.delete(tokenId);
        propURIMap.delete(tokenId);
        propNameMap.delete(tokenId);
        ownerMap.delete(tokenId);
        ctx.createMap(createTokensOfPrefix(owner)).delete(tokenId);
        decrementBalanceByOne(owner);
        increaseBurnedByOne();
        return true;
    }

    private static byte[] createTokensOfPrefix(Hash160 owner) {
        return Helper.concat(tokensOfKey, owner.toByteArray());
    }

    private static int getBalance(Hash160 account) {
        Integer balance = balanceMap.getInteger(account.toByteArray());
        return balance != null ? balance : 0;
    }

    private static void incrementBalanceByOne(Hash160 owner) {
        balanceMap.put(owner.toByteArray(), getBalance(owner) + 1);
    }

    private static void decrementBalanceByOne(Hash160 owner) {
        balanceMap.put(owner.toByteArray(), getBalance(owner) - 1);
    }

    private static void increaseMintedByOne() {
        int updatedTotalSupply = contractMap.getInteger(contractMinted) + 1;
        contractMap.put(contractMinted, updatedTotalSupply);
    }

    private static void increaseBurnedByOne() {
        int updatedTotalSupply = contractMap.getInteger(contractBurned) + 1;
        contractMap.put(contractBurned, updatedTotalSupply);
    }
}
