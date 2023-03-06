// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@uniswap/v2-core/contracts/interfaces/IUniswapV2Factory.sol";
import "@uniswap/v2-core/contracts/interfaces/IUniswapV2Pair.sol";
import "@uniswap/v2-periphery/contracts/interfaces/IUniswapV2Router02.sol";
import "./TaxableToken.sol";

contract Betted is ERC20, TaxableToken {
    IUniswapV2Router02 private _uniswapV2Router;
    IUniswapV2Factory private _uniswapV2Factory;
    IUniswapV2Pair private immutable _uniswapV2WethPair;

    bool private _isTransferringTax;
    
    constructor()
        ERC20("Betted", "BET")
        TaxableToken(true, 20, 5, 0x61E87D52d5a358eE83043a6d918A2E867e44bD2f, 0x5d7379995772b2eb7f617A524C49D170De4632DB) {

        _uniswapV2Router = IUniswapV2Router02(0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D);
        _uniswapV2Factory = IUniswapV2Factory(_uniswapV2Router.factory());
        _uniswapV2WethPair = IUniswapV2Pair(_uniswapV2Factory.createPair(address(this), _uniswapV2Router.WETH()));

        _baseTradingPair = address(_uniswapV2WethPair);
        tradingPairs[_baseTradingPair] = true;

        _mint(msg.sender, 1000000000 * (10 ** decimals()));
        _setSwapAtBalance(500000 * (10 ** decimals()));
    }

    function claimTaxes() public onlyRole(DEFAULT_ADMIN_ROLE) {
        _isTransferringTax = true;
        _swapAndClaimTaxes();
        _isTransferringTax = false;
    }

    function _transfer(address from, address to, uint256 amount) internal override {
        require(from != address(0), "ERC20: Cannot transfer from the zero address");
        require(amount > 0, "ERC20: Must transfer more than zero");

        if (!_isTransferringTax && _isBalanceEnoughToSwap(balanceOf(address(this))) && tradingPairs[to]) {
            _isTransferringTax = true;
            _swapAndClaimTaxes();
            _isTransferringTax = false;
        }

        uint amountToTransfer = amount;

        if (!_isTransferringTax) {
            if (tradingPairs[from] || tradingPairs[to]) {
                if (isTaxEnabled() && !hasRole(EXCLUDED_FROM_TAX_ROLE, from) && !hasRole(EXCLUDED_FROM_TAX_ROLE, to)) {
                    uint marketingTaxFee = _getMarketingTaxFee(amountToTransfer);
                    uint devTaxFee = _getDevTaxFee(amountToTransfer);
                    amountToTransfer = amount - marketingTaxFee - devTaxFee;

                    if ((marketingTaxFee + devTaxFee) > 0) {
                        super._transfer(from, address(this), marketingTaxFee + devTaxFee);
                    }
                }
            }
        }

        super._transfer(from, to, amountToTransfer);
    }

    function _swapAndClaimTaxes() private {
        uint tokensToSwap = balanceOf(address(this));
        if (tokensToSwap > _balanceToSwapAt * 5) {
            tokensToSwap = _balanceToSwapAt * 5;
        }

        _swapTokensForEth(tokensToSwap);
        _sendEthToTaxRecipients();
    }

    function _swapTokensForEth(uint256 amount) private {
        address[] memory path = new address[](2);
        path[0] = address(this);
        path[1] = _uniswapV2Router.WETH();

        _approve(address(this), address(_uniswapV2Router), amount);
        _uniswapV2Router.swapExactTokensForETH(
            amount, 0, path, address(this), block.timestamp
        );
    }

    receive() external payable {}
    fallback() external payable {}
}
