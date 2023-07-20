package com.peonlee.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peonlee.data.comment.CommentRepository
import com.peonlee.data.handle
import com.peonlee.data.model.Comment
import com.peonlee.data.model.ProductDetail
import com.peonlee.data.model.ProductRatingType
import com.peonlee.data.model.Score
import com.peonlee.data.product.ProductRepository
import com.peonlee.data.review.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val reviewRepository: ReviewRepository,
    private val commentRepository: CommentRepository
) : ViewModel() {
    lateinit var productDetail: ProductDetail
        private set

    private val _productRatingType = MutableSharedFlow<ProductRatingType>()
    val productRatingType: SharedFlow<ProductRatingType> = _productRatingType.asSharedFlow()

    private val _productDetailItemList = MutableStateFlow<List<ProductDetailListItem>>(emptyList())
    val productDetailItemList: StateFlow<List<ProductDetailListItem>> = _productDetailItemList.asStateFlow()

    fun start(productId: Int) {
        viewModelScope.launch {
            val commentsResponse = async { commentRepository.getProductComments(productId) }
            if (::productDetail.isInitialized.not()) {
                val productDetailResponse = async { productRepository.getProductDetail(productId) }
                productDetailResponse.await().handle({
                    productDetail = it
                    val itemList = mutableListOf<ProductDetailListItem>()
                    itemList.addAll(it.mapToPresentation())
                    commentsResponse.await().handle({ comments ->
                        itemList.addAll(comments.mapToPresentation())
                    })
                    _productDetailItemList.value = itemList
                    _productRatingType.emit(it.productRatingType)
                })
            } else {
                val itemList = mutableListOf<ProductDetailListItem>()
                itemList.addAll(productDetail.mapToPresentation())
                commentsResponse.await().handle({ comments ->
                    itemList.addAll(comments.mapToPresentation())
                })
                _productDetailItemList.value = itemList
                _productRatingType.emit(productDetail.productRatingType)
            }
        }
    }

    private fun ProductDetail.mapToPresentation() = listOf(
        ProductDetailListItem.Product(
            id = productId.toLong(),
            imageUrl = imageUrl,
            productName = name,
            price = price,
            upvoteRate = score.likeRatio,
            reviewCount = 10, // TODO review count
            eventList = promotionList.map { promotion ->
                ProductDetailListItem.Event(
                    retailerType = enumValueOf(promotion.retailerType),
                    promotionType = enumValueOf(promotion.promotionType)
                )
            }
        ),
        ProductDetailListItem.Divider(1),
        ProductDetailListItem.Score(
            id = 2,
            rateCount = score.totalCount,
            upvoteRate = score.likeRatio,
            downvoteRate = if (score.totalCount == 0) 0 else 100 - score.likeRatio
        ),
        ProductDetailListItem.Divider(3)
    )

    private fun List<Comment>.mapToPresentation(): List<ProductDetailListItem> {
        val itemList = mutableListOf<ProductDetailListItem>()
        if (isEmpty()) {
            itemList.add(ProductDetailListItem.NoneReview(id = 4))
        } else {
            itemList.add(ProductDetailListItem.ReviewHeader(id = 5, reviewCount = size)) // TODO review size
            itemList.addAll(
                map { comment ->
                    ProductDetailListItem.Review(
                        id = comment.productCommentId.toLong(),
                        nickname = comment.memberNickName,
                        writeDate = comment.createdAt,
                        likeType = comment.productLikeType,
                        reviewText = comment.content,
                        isLike = comment.liked,
                        likeCount = comment.likeCount,
                        isMine = comment.isOwner
                    )
                }
            )
        }
        return itemList
    }

    fun likeProduct(productId: Int) {
        viewModelScope.launch {
            productRepository.likeProduct(productId).handle({
                _productRatingType.emit(ProductRatingType.LIKE)
                updateScore(it)
            })
        }
    }

    fun dislikeProduct(productId: Int) {
        viewModelScope.launch {
            productRepository.dislikeProduct(productId).handle({
                _productRatingType.emit(ProductRatingType.DISLIKE)
                updateScore(it)
            })
        }
    }

    fun cancelLikeProduct(productId: Int) {
        viewModelScope.launch {
            productRepository.cancelLikeProduct(productId).handle({
                _productRatingType.emit(ProductRatingType.NONE)
                updateScore(it)
            })
        }
    }

    private fun updateScore(score: Score) {
        val newList = _productDetailItemList.value.toMutableList()
        val detailItemIndex = newList.indexOfFirst { it is ProductDetailListItem.Product }
        newList[detailItemIndex] = (newList[detailItemIndex] as ProductDetailListItem.Product).copy(upvoteRate = score.likeRatio)

        val scoreItemIndex = newList.indexOfFirst { it is ProductDetailListItem.Score }
        newList[scoreItemIndex] = (newList[scoreItemIndex] as ProductDetailListItem.Score).copy(
            rateCount = score.totalCount,
            upvoteRate = score.likeRatio,
            downvoteRate = if (score.totalCount == 0) 0 else 100 - score.likeRatio
        )
        _productDetailItemList.value = newList
    }

    fun deleteReview(productId: Int) {
        viewModelScope.launch {
            reviewRepository.deleteReview(productId).handle({
                // TODO remove review Item
            })
        }
    }
}
